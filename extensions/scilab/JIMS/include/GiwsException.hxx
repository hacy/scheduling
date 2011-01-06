/* Generated by GIWS (version 1.1.0) */
/*

Copyright 2007-2008 INRIA

Author : Sylvestre Ledru & others

This software is a computer program whose purpose is to hide the complexity
of accessing Java objects/methods from C++ code.

This software is governed by the CeCILL-B license under French law and
abiding by the rules of distribution of free software.  You can  use, 
modify and/ or redistribute the software under the terms of the CeCILL-B
license as circulated by CEA, CNRS and INRIA at the following URL
"http://www.cecill.info". 

As a counterpart to the access to the source code and  rights to copy,
modify and redistribute granted by the license, users are provided only
with a limited warranty  and the software's author,  the holder of the
economic rights,  and the successive licensors  have only  limited
liability. 

In this respect, the user's attention is drawn to the risks associated
with loading,  using,  modifying and/or developing or reproducing the
software by the user in light of its specific status of free software,
that may mean  that it is complicated to manipulate,  and  that  also
therefore means  that it is reserved for developers  and  experienced
professionals having in-depth computer knowledge. Users are therefore
encouraged to load and test the software's suitability as regards their
requirements in conditions enabling the security of their systems and/or 
data to be ensured and,  more generally, to use and operate it in the 
same conditions as regards security. 

The fact that you are presently reading this means that you have had
knowledge of the CeCILL-B license and that you accept its terms.
*/


#ifndef __GIWSEXCEPTION__
#define __GIWSEXCEPTION__
#include <iostream>
#include <string>
#include <string.h>
#include <stdlib.h>
#include <jni.h>

//#ifndef _MSC_VER /* Defined anyway with Visual */
#if !defined(bbyte)
typedef signed char bbyte;
#else
#pragma message("Byte has been redefined elsewhere. Some problems can happen")
#endif
//#endif
#include <exception>

namespace GiwsException {


/**
* Parent class for exceptions which may occure in JNI code.
*/
class JniException : public std::exception
{

/** Error message to display */
std::string m_oErrorMessage;

/** Java description of the exception*/
std::string m_oJavaMessage;

/** Java stackTrace when the exception occured */
std::string m_oJavaStackTrace;

/** Name of the exception (ie class name).*/
std::string m_oJavaExceptionName;

/** The exception itself ... we store as a member otherwise JNI
complains about 'WARNING in native method: JNI call made with
exception pending' */
jthrowable javaException;

public:

/**
* Each subclass of JniExcpetion should call the super constructor
* and the setErrorMessage function to set the message.
* @param curEnv java environment where the exception occured.
*/
JniException(JNIEnv * curEnv) throw() ;

virtual ~JniException(void) throw();

/**
* @return a description of the exception
*/
virtual const char * what(void) const throw();

/**
* @return Java description of the exception.
*/
std::string getJavaDescription(void) const throw();

/**
* @return Java stack trace where the exception occured.
*/
std::string getJavaStackTrace(void) const throw();

/**
* Get the name of the exception (ie its class name).
*/
std::string getJavaExceptionName(void) const throw();

protected:

/**
* Set the error message that the exception should print.
*/
void setErrorMessage(const std::string & errorMessage);

/**
* Get the message that the exception will print.
*/
std::string getErrorMessage(void) const;

private:
  /**
* @return error message of the exception.
*/
std::string retrieveExceptionMessage(JNIEnv * curEnv);
/**
* @return full stack trace when the exception occured.
*/
std::string retrieveStackTrace(JNIEnv * curEnv);

/**
* @return string containing the name of the exception (ie its class name).
*/
std::string retrieveExceptionName(JNIEnv * curEnv);
/**
* To be called when all the information about the exceptions have been
* retrived.
* Remove the exception from the environment.
*/
void closeException(JNIEnv * curEnv);

/**
* Convert a Java string (jstring) into a C++ string
*/
std::string convertJavaString(JNIEnv * curEnv, jstring javaString);
};

/**
* Exception that should be thrown when allocation of Java ressources from C++
* code fails (sur as NewDoubleArray or NewStringUTF).
*/
class JniBadAllocException : public JniException
{
public:

JniBadAllocException(JNIEnv * curEnv) throw();
virtual ~JniBadAllocException(void) throw();
};

/**
* Exception that should be thrown when a call to a Java method
* using Jni throw an exception.
* If possible, user should try to avoid this sitution because of the loss
* of information.
*/
class JniCallMethodException : public JniException
{
public:

  /**
   * @param curEnv java envirnonment where the exception occured.
   */
  JniCallMethodException(JNIEnv * curEnv) throw();

  virtual ~JniCallMethodException(void) throw();
};

/**
* Exception that should be thrown when Jni code could not find a Java class
*/
class JniClassNotFoundException : public JniException
{
public:

/**
* @param className name of the class which haven't been found
*/
JniClassNotFoundException(JNIEnv * curEnv, const std::string & className) throw();

virtual ~JniClassNotFoundException(void) throw();

};

/**
* Exception that should be thrown when Jni code could not find a Java method
*/
class JniMethodNotFoundException : public JniException
{
public:

/**
* @param className name of the method which haven't been found
*/
JniMethodNotFoundException(JNIEnv * curEnv, const std::string & methodName) throw();
virtual ~JniMethodNotFoundException(void) throw();

};

/**
* Exception that should be thrown when a call to a Java method
* using Jni throw an exception.
* If possible, user should try to avoid this sitution because of the loss
* of information.
*/
class JniObjectCreationException : public JniException
{
public:

/**
* @param curEnv java envirnonment where the exception occured.
*/
JniObjectCreationException(JNIEnv * curEnv, const std::string & className) throw();
virtual ~JniObjectCreationException(void) throw();

};


/**
* Exception that should be thrown when a call to the Java monitor
* failed
*/
class JniMonitorException : public JniException
{
public:

/**
* @param curEnv java envirnonment where the exception occured.
*/
JniMonitorException(JNIEnv * curEnv, const std::string & className) throw();
virtual ~JniMonitorException(void) throw();

};


}
#endif

