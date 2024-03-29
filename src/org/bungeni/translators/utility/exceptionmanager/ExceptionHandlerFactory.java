/**
 Copyright (C) 2010, Africa i-Parliaments , UNDESA
 *
 *

This program is free software; you can redistribute it
and/or modify it under the terms of the GNU General Public
License as published by the Free Software Foundation; either
version 2 of the License, or (at your option) any later
version. This program is distributed in the hope that it
will be useful, but WITHOUT ANY WARRANTY; without even the
implied warranty of MERCHANTABILITY or FITNESS FOR A
PARTICULAR PURPOSE. See the GNU General Public License for
more details. You should have received a copy of the GNU
General Public License along with this program; if not,
write to the Free Software Foundation, Inc., 59 Temple
Place, Suite 330, Boston, MA 02111-1307 USA

 */
package org.bungeni.translators.utility.exceptionmanager;

import org.bungeni.translators.utility.exceptionmanager.handlers.IExceptionHandler;

/**
 *
 * @author Ashok Hariharan
 *
 */
public class ExceptionHandlerFactory {

    /**
     * Creates an instance of a class with a IExceptionHandler interface
     * @param matchedExceptionClass
     * @return
     * @throws ClassNotFoundException
     * @throws InstantiationException
     * @throws IllegalAccessException
     */
    public static IExceptionHandler getExceptionHandler(String matchedExceptionClass) throws ClassNotFoundException, InstantiationException, IllegalAccessException {
             Class handlerClass = Class.forName(matchedExceptionClass);
             IExceptionHandler exceptionHandler =
                        (IExceptionHandler) handlerClass.newInstance();
              return exceptionHandler;
    }


}
