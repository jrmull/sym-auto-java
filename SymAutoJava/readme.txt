SymAutoJava Installation


1) This application assumes version 1.7 of the java runtime (JRE) has been installed.  If not installed, The JRE installation package may be found at http://java.com/en/download/manual.jsp

2) Unzip the SymAutoJava package to a location of your choosing

3) Edit the config.dat file to provide the server and user login values SymAutoJava will use when connecting to Symitar.    

4) From a command line, change to the location where SymAutoJava is installed and type the following:

SymAutoJava <job name> <queue>  

Where <job name> is the name of the job to be run and <queue> is the queue number to use for the job.  Job name is required, but queue is optional.  Queue 0 (zero) will be used if not specified.  







* If an error such as "java not found" happens, edit the SymAutoJava.bat file to include the full path the the java runtime.  For example, if the java runtime is installed at C:\Program Files\Java\jre7, change SymAutoJava.bat from this:


java -Djava.util.logging.config.file=config\log.conf com.repdev.SymAutoJava %*


to this:


"C:\Program Files\Java\jre7\bin\java" -Djava.util.logging.config.file=config\log.conf com.repdev.SymAutoJava %* 


** Note, the path to your java runtime may be different than this example. 




















