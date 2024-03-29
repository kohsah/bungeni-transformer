package org.bungeni.translators.translator;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.InvalidPropertiesFormatException;
import java.util.List;
import java.util.Properties;
import java.util.ResourceBundle;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.stream.StreamSource;
import javax.xml.xpath.XPathExpressionException;
import org.bungeni.translators.configurations.OAConfiguration;
import org.bungeni.translators.configurations.Parameter;
import org.bungeni.translators.configurations.steps.OAPipelineStep;
import org.bungeni.translators.exceptions.DocumentNotFoundException;
import org.bungeni.translators.exceptions.TranslationFailedException;
import org.bungeni.translators.exceptions.TranslationStepFailedException;
import org.bungeni.translators.exceptions.ValidationFailedException;
import org.bungeni.translators.exceptions.XSLTBuildingException;
import org.bungeni.translators.globalconfigurations.GlobalConfigurations;
import org.bungeni.translators.translator.XMLSourceFactory.XMLSourceType;
import org.bungeni.translators.utility.dom.DOMUtility;
import org.bungeni.translators.utility.exceptionmanager.ValidationError;
import org.bungeni.translators.utility.files.FileUtility;
import org.bungeni.translators.utility.files.OutputXML;
import org.bungeni.translators.utility.runtime.CloseHandle;
import org.bungeni.translators.utility.runtime.Outputs;
import org.bungeni.translators.utility.schemavalidator.SchemaValidator;
import org.bungeni.translators.utility.streams.StreamSourceUtility;
import org.bungeni.translators.utility.transformer.XSLTTransformer;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;


/***

 * @author Ashok Hariharan
 */
public class OATranslator implements org.bungeni.translators.interfaces.Translator {

    /* The instance of this Translator */
    private static OATranslator instance = null;

    /* This is the logger */
     private static org.apache.log4j.Logger log              =
        org.apache.log4j.Logger.getLogger(OATranslator.class.getName());

    /* The resource bundle for the messages */
    private ResourceBundle resourceBundle;

    //AH-23-06-2010 moved to translator_config
    //private String defaultPipelinePath ;

    private Boolean cachePipelineXSLT = false;
    private Boolean writeIntermediateOutputs = false;
    
    //The source type is by default ODF
    //!+XML_SOURCE_TYPE(ah, 27-09-2011)
    private XMLSourceFactory.XMLSourceType sourceType = XMLSourceFactory.XMLSourceType.ODF;
    //!+INPUT_PARAMETERS (ah, nov-2011) added to support input parameters to the translator
    /**
     * This is the input parameter passed from the caller to the pipeline
     */
    //!+BICAMERAL made value Object type
    private HashMap<String, String> pipelineInputParameters;

    /**
     * Private constructor used to create the Translator instance
     * @throws IOException
     * @throws InvalidPropertiesFormatException
     */
    private OATranslator() throws InvalidPropertiesFormatException, IOException, TranslationFailedException, XPathExpressionException {

        //!+TRANSLATOR_PROPERTIES(ah, oct-2011) Translator properties were moved into the main configuration
        //file -- translators are now configured via a single configurationfile
        //use OAConfiguration.getProperties() to get the propeties object of the translation
        //!+BICAMERAL made value object type
        this.pipelineInputParameters = new HashMap<String,String>();
    }

    /**
     * Get the current instance of the Translator
     * @return the translator instance
     */
    public static synchronized OATranslator getInstance() {

        // if the instance is null create a new instance
        if (instance == null) {

            // create the instance
            try {
                instance = new OATranslator();
            } catch (Exception e) {
                log.error("getInstance", e);
            }
        }

        // otherwise return the instance
        return instance;
    }

    private void setupConfiguration(String configurationFilePath) throws IOException, XPathExpressionException, TranslationFailedException {
        // create the Properties object
        //!+FIX_THIS_LATER (ah, oct-2011) The pipeline caching logic will also need
        // to take into account different input pipelines !!!
        //this is the config_<type>.xml
        //FIXED -- nothing to change here -- the default cache checking behavior is fine, its
        //checked on the specific pipeline i.e. if the output xslt exists
        String translatorConfigurationPath = GlobalConfigurations.getApplicationPathPrefix()   + configurationFilePath;
        //
        // get the translator configuration file
        //
        try {
            setupTranslatorConfiguration(translatorConfigurationPath);
        } catch (ParserConfigurationException ex) {
            log.error("Error while getting transltor configuration", ex);
        } catch (SAXException ex) {
            log.error("Error while getting transltor configuration", ex);
        } catch (XPathExpressionException ex) {
            log.error("Error while getting transltor configuration", ex);
        }

        //
        // verify the translator configurtion file
        //

        if (!OAConfiguration.getInstance().verify()) {
                throw new TranslationFailedException(
                        resourceBundle.getString("TRANSLATION_CONFIGURATION_FAIL"));
            }

        //
        // get the translation properties
        //
        Properties properties = OAConfiguration.getInstance().getProperties();

        if (properties == null ) {
            throw new InvalidPropertiesFormatException("Invalid format for translator configuration properties");
        }

        // create the resource bundle
        this.resourceBundle = ResourceBundle.getBundle(properties.getProperty("resourceBundlePath"));

        // check if pipeline xslt needs to be cached
        this.cachePipelineXSLT = Boolean.parseBoolean(properties.getProperty("cachePipelineXSLT"));

        //check if writeIntermediateOutputs property is set
        String strInterOuputs = properties.getProperty("writeIntermediateOutputs");
        //if not set, default to false
        if (strInterOuputs == null) {
            // if intermediate outputs == null
            this.writeIntermediateOutputs = false;
        } else {
            //use set value
            this.writeIntermediateOutputs = Boolean.parseBoolean(strInterOuputs);
        }

        String strSourceType = properties.getProperty("inputXmlSource");

        this.sourceType = XMLSourceType.valueOf(strSourceType);

        log.info("OATRANSLATOR ; translatorConfigPath :" + translatorConfigurationPath + " ;resourceBundle :" + this.resourceBundle + " ;cachePipelineXSLT : " + this.cachePipelineXSLT);

    }
    /**
     * Prevent cloning of singleton instance
     */
    @Override
    public Object clone() throws CloneNotSupportedException {
        throw new CloneNotSupportedException();
    }





    /**
     * Transforms the document at the given path using the pipeline at the given path
     *
     * Translation type is autom
     *
     * @param aDocumentPath the path of the document to translate
     * @param aPipelinePath the path of the pipeline to use for the translation
     * @param inputParameters HashMap/ Dictionary of input parameters for the translator. These
     * need to be declared in the config of the pipeline in both input steps.
     * @return a hashmap containing handles to all steps which have output = true set, the map key is the type of step
     * e.g. for input steps the key is "input" for output steps the key is output. The finaal ANxml output is "final"
     * @throws Exception
     * @throws TransformerFactoryConfigurationError
     */
    public HashMap<String, File> translate(
            String aDocumentPath,
            String configFilePath
            )
            throws TransformerFactoryConfigurationError, Exception {
        HashMap<String, File> translatedFiles = new HashMap<String, File>();

        try {

            /***
             * !+FIX_THIS_LATER -- the translator configuration is loaded
             * here instead of in the constructor.
             * TO BE FIXED in the EDITOR
             */
            this.setupConfiguration(configFilePath);

            /**
             * Source type is detected from configuration during object setup, not
             * done here anymore
             */

            /**
             * Get the source instance
             */
            IXMLSource sourceInstance = this.sourceType.getObjectInstance();


            /***
             * Get the appropriate input source stream
             */

            StreamSource xmlDocument = sourceInstance.getSource(aDocumentPath);

            /***
             * Get the translator configuration
             */
           
            StreamSource outputStepsProcessedDoc = null;

            //we use a nested exception handler here to specifically catch intermediary
            //exceptions

            try {
                /**
                 * Apply input steps
                 */

                StreamSource inputStepsProcessedDoc = this.applyInputSteps(translatedFiles, xmlDocument);

                StreamSource replaceStepsProcessedDoc = inputStepsProcessedDoc;
               
                if (OAConfiguration.getInstance().hasReplaceSteps()) {
                    /**
                     * Apply the replace steps
                     */
                  replaceStepsProcessedDoc = this.applyReplaceSteps(
                          translatedFiles,
                          inputStepsProcessedDoc
                          );
                }
                /**
                 * Finally apply the output steps
                 */
                outputStepsProcessedDoc = replaceStepsProcessedDoc;
                if (OAConfiguration.getInstance().hasOutputSteps()) {
                   outputStepsProcessedDoc = this.applyOutputSteps(
                           translatedFiles,
                           replaceStepsProcessedDoc
                           );
                }

             } catch (Exception e) {
                    //(DEBUG_USEFUL)+ This is a useful catch-all point to put a break point if the
                    // translation is failing !!!
                    // get the message to print
                    String message = resourceBundle.getString("TRANSLATION_TO_METALEX_FAILED_TEXT");
                    System.out.println(message);
                    // print the message and the exception into the logger
                    log.fatal((new TranslationStepFailedException(message)), e);
                    // RETURN null
                    return null;
                }
            //!+PIPELINE (ah, oct-2011) -- the pipeline is non-mandatory now
            StreamSource anXmlStream  = null;
            if (OAConfiguration.getInstance().hasPipelineXML()) {
                /***
                 * Build the XSLT pipeline
                 */
                List<File> xsltPipes = this.buildXSLTPipeline();
                /**
                 * Transform the Metalex using the built XSLT
                 */
                StreamSource inputXmlStream = outputStepsProcessedDoc; 
                for (File xslt : xsltPipes) {
                    inputXmlStream = this.translateToAkomantoso(xslt, inputXmlStream);
                    
                 }
                
                anXmlStream = inputXmlStream;
            } else {
                anXmlStream = outputStepsProcessedDoc; 
            }

            StreamSource anXmlFinalStream = anXmlStream;
            if (OAConfiguration.getInstance().hasPostXmlSteps()) {
                /***
                 * Finally call the Add namespace XSLT
                 */
                anXmlFinalStream = this.applyPostXmlSteps(anXmlStream);
            }
            /**
             * Final Output
             */
            //!+PIPELINE (ah, oct-2011) -- if there is no pipeline and no output steps then the 2 outputs
            //will be exactly the same
            OutputXML oxmlFinal = StreamSourceUtility.getInstance().writeStreamSourceToFile(
                    anXmlFinalStream, 
                    "akoma_", 
                    ".xml"
                    );
            translatedFiles.put("final", oxmlFinal.outputxmlFile);
            //!+WARNING_CHECK_CLOSE
            oxmlFinal.closeHandles();
            // validate the produced document
            //AH-8-03-11 COMMENTED OUT FOR NOW UNTIL TESTED
            //SchemaValidator.getInstance().validate(fileToReturn, aDocumentPath, this.akomantosoSchemaPath);

            // write the stream to a File and return it
            // return fileToReturn;
        } catch (TransformerException e) {

            // get the message to print
            String message = resourceBundle.getString("TRANSLATION_FAILED_TEXT");

            System.out.println(message);

            // print the message and the exception into the logger
            log.fatal((new TranslationFailedException(message)).getStackTrace());

            // RETURN null
            return null;
        } catch (SAXException e) {

            // get the message to print
            String message = resourceBundle.getString("VALIDATION_FAILED_TEXT");

            System.out.println(message);

            // print the message and the exception into the logger
            log.fatal((new ValidationFailedException(message)).getStackTrace());

            // RETURN null
            return null;
        } catch (ParserConfigurationException e) {

            // get the message to print
            String message = resourceBundle.getString("VALIDATION_FAILED_TEXT");

            System.out.println(message);

            // print the message and the exception into the logger
            log.fatal((new ValidationFailedException(message)).getStackTrace());

            // RETURN null
            return null;
        } catch (IOException e) {

            // get the message to print
            String message = resourceBundle.getString("IOEXCEPTION_TEXT");

            System.out.println(e.getMessage());

            // print the message and the exception into the logger
            log.fatal((new DocumentNotFoundException(message)).getStackTrace());

            // RETURN null
            return null;
        }

        return translatedFiles;
    }


   /**
    * Alternate way of calling translate() with input XSLT parameters,
    * pass all the input parameters in the HashMap
    * @param aDocumentPath
    * @param configFilePath
    * @param inputParameters
    * @return
    * @throws TransformerFactoryConfigurationError
    * @throws Exception
    */
   public HashMap<String, File> translate(
            String aDocumentPath,
            String configFilePath,
            HashMap inputParameters
            )
            throws TransformerFactoryConfigurationError, Exception {
            this.pipelineInputParameters = inputParameters;
            return this.translate(aDocumentPath, configFilePath);
            
    }
    /**
     * Provides access to the TranslatorConfig
     * @param aConfigurationPath
     * @return
     * @throws ParserConfigurationException
     * @throws SAXException
     * @throws IOException
     * @throws XPathExpressionException
     */
    private void setupTranslatorConfiguration(String aConfigurationPath) throws ParserConfigurationException, SAXException, IOException, XPathExpressionException{
            // get the File of the configuration
            InputSource isourceConfiguration = null ;
            try {
                isourceConfiguration = FileUtility.getInstance().FileAsInputSource(
                        aConfigurationPath
                        );

                Document configurationDoc = OADocumentBuilderFactory.getInstance().
                        getDBF().newDocumentBuilder().
                            parse(
                                isourceConfiguration
                            );
                // create the configuration
                OAConfiguration configuration = OAConfiguration.getInstance();
                configuration.setConfiguration(configurationDoc);
            } catch (Exception ex) {
                log.error("Error in setupTranslatorConfiguration ", ex);
            } finally {
                CloseHandle.closeQuietly(isourceConfiguration);
            }
    }


     private List<File> buildXSLTPipeline() throws XPathExpressionException, SAXException, IOException, ParserConfigurationException, TransformerFactoryConfigurationError, TransformerException{
            List<File> pipelines = new ArrayList<File>(0);
            List<OAPipelineStep> pipelineSteps  = OAConfiguration.getInstance().getXsltPipeline();
            for (OAPipelineStep oAPipelineStep : pipelineSteps) {
                String pipeName = oAPipelineStep.getPipelineName();
                String pipeHref = oAPipelineStep.getPipelineHref();
                File xsltPipe = getXSLTPipeline(pipeName, pipeHref);
                pipelines.add(xsltPipe);
            }
            return pipelines;
     }
    /**
     * Builds the XSLT pipeline - once built , returns the cached copy
     * @param aPipelinePath
     * @return
     * @throws XPathExpressionException
     * @throws SAXException
     * @throws IOException
     * @throws ParserConfigurationException
     * @throws TransformerFactoryConfigurationError
     * @throws TransformerException
     */
    private File getXSLTPipeline(String pipeName, String aPipelinePath) throws
            XPathExpressionException,
            SAXException, IOException,
            ParserConfigurationException,
            TransformerFactoryConfigurationError,
            TransformerException{
        /**
         * We cache the XSLT pipeline after building it
         */
        File xslt = null;
        String fullPipeName = pipeName + "_xslt_pipeline.xsl";
        File outputXSLT = Outputs.getInstance().File(fullPipeName);
        /**
         * Check if the cache parameter is enabled
         */
        if (this.cachePipelineXSLT && outputXSLT.exists()) {
            /*
             * Use the cached XSLT if it exists
             */
            xslt = outputXSLT;
            log.info("!!!!!!!! USING CACHED TEMPLATE !!!!!!!");
        } else {
            //otherwise build the pipeline and return it
            xslt = this.buildXSLT(aPipelinePath);
            FileUtility.getInstance().copyFile(xslt, 
                    Outputs.getInstance().File(fullPipeName));
        }
        return xslt;
    }

    /**
     * Applys the input steps in the TranslatorConfig on the merged ODF
     * @param ODFDocument
     * @param configuration
     * @return
     * @throws TransformerFactoryConfigurationError
     * @throws Exception
     */
    public StreamSource applyInputSteps(HashMap<String, File> translatedFiles, StreamSource ODFDocument)
            throws TransformerFactoryConfigurationError, Exception {
           // applies the input steps to the StreamSource of the ODF document
            HashMap<String,Parameter> resolvedParameterMap = this.resolveParameterMap("input");
            StreamSource iteratedDocument = OAXSLTStepsResolver.getInstance().resolve(ODFDocument,
                                                    resolvedParameterMap,
                                                    OAConfiguration.getInstance().getInputSteps());

            if (OAConfiguration.getInstance().hasWriteInputsStream() &&
                    this.writeIntermediateOutputs == true) {
                // write output to file
                OutputXML oxmlInput = StreamSourceUtility.getInstance().writeStreamSourceToFile(
                        iteratedDocument, "inp", ".xml"
                        );
                translatedFiles.put("input", oxmlInput.outputxmlFile);
                
                iteratedDocument = oxmlInput.outputxmlStream;
            }
            return iteratedDocument;
    }

    /**
     * This API resolves the incoming parameters with the ones declared in configuration
     * 
     * Input parameters are declared on the pipe using the parameter syntax.
     * 
     * Declared parameters can be set by the caller of the pipe or by declaring defaults on the pipe.
     * 
     * This api merges the input parameters with the defaults - i.e. if you specify an input parameter,
     * it will use the specified input param instead of the default.
     * 
     * @param forStep
     * @return
     * @throws XPathExpressionException
     * @throws Exception 
     */
    private HashMap<String,Parameter> resolveParameterMap(String forStep) throws XPathExpressionException, Exception {
        //We merge the input parameter map with the parameters specified in Configuration
        HashMap<String,Parameter> resolvedMap = new HashMap<String,Parameter>();

        //first get the parameters from Configuration
        //this will include default declarations
        HashMap<String,Parameter> configParameters = 
                OAConfiguration.getInstance().getParameters(forStep);

        //Validate the pipeline parameters - we cannot input parameters which are
        //not declared in the config
        //Now iterate through the input parameter map and identify parameters to merge
        // first get the parameters specified by the caller and set them on the resolvedMap
        for (String key : this.pipelineInputParameters.keySet()){
            //if the input parameter exists in configuration
            if (configParameters.containsKey(key)) {
                Parameter configParameter = configParameters.get(key);
                String inputParamValue = this.pipelineInputParameters.get(key);
                // set the value in the map - the setValue coerces the value 
                // into the correct format based on the type
                configParameter.setValue(inputParamValue);
                resolvedMap.put(key, configParameter);
            } else {
                log.warn("WARNING !!!!: an undeclared paramter: "+ key +"  was passed as an input parameter "
                        + "This parameter will be ignored until declared in the configuraiton xml ");
            }
         }

        //Now iterate through the config parameters and identify missing ones to default
        for (String key : configParameters.keySet()) {
            if (!resolvedMap.containsKey(key)) {
                //config parameters support String values and XML nodes
                Parameter defaultConfigValue = configParameters.get(key);
                resolvedMap.put(key, defaultConfigValue);
                /**if (defaultConfigValue.getClass().equals(String.class)) {
                    String defaultConfigValueString = ((String)defaultConfigValue).trim();
                    if (defaultConfigValueString.isEmpty()) {
                       log.warn("WARNING !!!!: One of the default parameters : " + key + " has a empty default value in configuration ");
                    }
                    resolvedMap.put(key, defaultConfigValueString);
                } else {
                    //the only other type allowed is XML
                    //!+XSLT_PARAM_XML (ah, 12-04-2012) - if its not a string class, its an xml document
                    // wrapped in a saxon document wrapper 
                    resolvedMap.put(key, defaultConfigValue);
                }**/
            }
        }

        return resolvedMap;

    }

    /***
     * Applies the replacement steps in the TranslatorConfig on the output of
     * the input steps
     * @param ODFDocument
     * @param configuration
     * @return
     * @throws TransformerFactoryConfigurationError
     * @throws Exception
     */
    public StreamSource applyReplaceSteps(HashMap<String, File> translatedFiles, StreamSource ODFDocument)
                 throws TransformerFactoryConfigurationError, Exception {
            // applies the map steps to the StreamSource of the ODF document
            StreamSource iteratedDocument = OAReplaceStepsResolver.resolve(ODFDocument,
                                                    OAConfiguration.getInstance());
            if (OAConfiguration.getInstance().hasWriteReplacementsStream() &&
                    this.writeIntermediateOutputs == true) {
                OutputXML oxmlRepl = StreamSourceUtility.getInstance().writeStreamSourceToFile(iteratedDocument, "repl", ".xml");
                translatedFiles.put("replace", oxmlRepl.outputxmlFile);
                iteratedDocument = oxmlRepl.outputxmlStream;
            }
            return iteratedDocument;
    }

    /***
     * Appies the output steps in the TranslatorConfig on the output of the
     * replacement steps
     * @param ODFDocument
     * @param configuration
     * @return
     * @throws TransformerFactoryConfigurationError
     * @throws Exception
     */
    public StreamSource applyOutputSteps(HashMap<String, File> translatedFiles, StreamSource ODFDocument)
                 throws TransformerFactoryConfigurationError, Exception {
         // apply the OUTPUT XSLT to the StreamSource
        //!+FIX_THIS output steps dont process parameters -
         StreamSource resultStream = OAXSLTStepsResolver.getInstance().resolve(
              ODFDocument,
              OAConfiguration.getInstance().getOutputSteps()
         );
         if (OAConfiguration.getInstance().hasWriteOutputsStream() && this.writeIntermediateOutputs == true) {
            // write output to file
                OutputXML oxmlOut = StreamSourceUtility.getInstance().writeStreamSourceToFile(resultStream, "out", ".xml");
                translatedFiles.put("output", oxmlOut.outputxmlFile);
                resultStream = oxmlOut.outputxmlStream;
         }
         return resultStream;
    }


    /***
     *
     * @param anXmlStream
     * @param configuration
     * @return
     * @throws TransformerFactoryConfigurationError
     * @throws Exception
     */
    public StreamSource applyPostXmlSteps(StreamSource anXmlStream)
             throws TransformerFactoryConfigurationError, Exception {
         // apply the OUTPUT XSLT to the StreamSource
         StreamSource resultStream = OAXSLTStepsResolver.getInstance().resolve(anXmlStream,
                                        OAConfiguration.getInstance().getPostXmlSteps()
                                        );
         return resultStream;
    }


    public StreamSource translateToAkomantoso(File xsltFile, StreamSource xmlStream)
            throws FileNotFoundException, TransformerException, UnsupportedEncodingException{
           StreamSource result = null;
           StreamSource ssXslt = null;
            try {
            ssXslt  = FileUtility.getInstance().FileAsStreamSource(xsltFile);
            XSLTTransformer xsltTransformer = XSLTTransformer.getInstance();
            result = xsltTransformer.transform(xmlStream, ssXslt);
            } catch (FileNotFoundException ex) {
                throw ex;
            } catch (TransformerException ex) {
                throw ex;
            } catch (UnsupportedEncodingException ex) {
                throw ex;
            } finally {
                CloseHandle.closeQuietly(ssXslt);
                CloseHandle.closeQuietly(xmlStream);
            }
            return result;
    }


    /**
     * Create and return an XSLT builded upon the instructions of the given pipeline.
     * @param aPipelinePath the pipeline upon which the XSLT will be created
     * @return a File containing the created XSLT
     * @throws ParserConfigurationException
     * @throws IOException
     * @throws SAXException
     * @throws XPathExpressionException
     * @throws TransformerException
     * @throws TransformerFactoryConfigurationError
     */
    public File buildXSLT(String aPipelinePath)
            throws XPathExpressionException, SAXException, IOException, ParserConfigurationException,
                   TransformerFactoryConfigurationError, TransformerException {
        try {

            // create the XSLT document starting from the pipeline
            Document pipeline = OAPipelineResolver.getInstance().resolve(aPipelinePath);

            // write the document to a File
            File resultFile = DOMUtility.getInstance().writeToFile(pipeline);

            // return the file
            return resultFile;
        } catch (Exception e) {

            // get the message to print
            String message = resourceBundle.getString("XSLT_BUILDING_FAILED_TEXT");

            System.out.println(message);

            // print the message and the exception into the logger
            log.fatal((new XSLTBuildingException(message)).getStackTrace());

            // RETURN null
            return null;
        }
    }

    public String getValidationErrors() {
        ArrayList<ValidationError> validationErrors = SchemaValidator.getInstance().getValidationErrors();
        StringBuilder               errorBuffer      = new StringBuilder();

        errorBuffer.append("<validationErrors>\n");

        if (validationErrors != null) {
            for (ValidationError error : validationErrors) {
                errorBuffer.append(error.getXmlString());
            }
        }

        errorBuffer.append("</validationErrors>");

        return errorBuffer.toString();
    }
}