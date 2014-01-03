/**
 *	SymAutoJava
 *  Copyright 2013, Shane Morrell, Mike Blumenthal
 *
 *  This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.repdev;

import java.io.File;
import java.io.FileReader;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.logging.ConsoleHandler;
import java.util.logging.Logger;
import java.util.logging.Handler;

/**
 * 
 *   
 * @author Shane Morrell, Mike Blumenthal
 */

public class SymAutoJava 
{		
	public static SymitarSession session; 
	private static final Logger log = Logger.getLogger( SymAutoJava.class.getName() );	
	
	public static void main(String[] args) throws Exception {
		
		SymAutoJava app = new SymAutoJava();
		
		try {					
			//Load configuration
			Properties config = app.loadConfig("config/config.dat");							
			int sym = Integer.parseInt(config.getProperty("sym"));
			String server = config.getProperty("server");
			int port = Integer.parseInt(config.getProperty("port"));
			String userId = config.getProperty("userID");
			String user = config.getProperty("user");
			String pw = config.getProperty("pw");
			
			
			//Job name argument required
			if (args == null || args.length == 0 || args[0] == null || args[0].trim().length() == 0) {
				log.severe("Job name parameter required! or -unlock to unlock consoles");			
				System.exit(1);
			}		
			String jobName = args[0];
			
			//Connect to Symitar
			session =  new DirectSymitarSession();
			log.info("Connecting to Symitar...    server:" + server + " port:" + port + "  user:" + user);
			SessionError error = session.connect(server, port, user, pw, sym, userId);
			if (error != SessionError.NONE){
				log.severe("Connection failed!  job: " + jobName + "  server: " + server + " port:" + port +  "  user: " + user +  "  " + error.toString());	
				System.exit(1);
			}
			log.info("Connection established");
			
			//if the argument is to unlock consoles do that, otherwise run a job
            if(jobName.toLowerCase().equals("-unlock"))
            {

                //Run the Job
            	error = session.UnlockConsoles();
                if (error != SessionError.NONE)
                {
                    log.severe("Connection failed!  Action: " + jobName + "  server: " + server + " port:" + port +  "  user: " + user +  "  " + error.toString());    
                    System.exit(1);
                }
                
            }
            else {
				//'queue' argument is optional
				int queue = -1;
				if (args.length > 1 && args[1] != null && args[1].length() > 0) {
					try {
						queue = Integer.parseInt(args[1]);
					}
					catch (NumberFormatException e) {
						log.severe("Invalid queue given: " + args[1] + "    If queue specified, it must be a number greater than or equal to 0");			
						System.exit(1);
					}
				}
				
				//'qtime' argument is optional
				int qtime = -1;
				if (args.length > 2 && args[2] != null && args[2].length() > 0) {
					try {
						qtime = Integer.parseInt(args[2]);
						log.info("Job Scheduled for : " + qtime);
					}
					catch (NumberFormatException e) {
						log.severe("Invalid time scheduled: " + args[2] + "    If qtime specified, it must be a number greater than or equal to 0");			
						System.exit(1);
					}
				}
				
				//'JobPrompts' argument is optional
				String JobPrompts = "";
				if (args.length > 3) {
					log.fine("Prompt args:" + args[3]);
            		JobPrompts = args[3];
				}
				
				
				//Run the Job
				SymitarFile symFile = new SymitarFile(sym, jobName, FileType.REPGEN);
				int tseq = -1;
				if (JobPrompts != "") {
					SymitarSession.RunRepgenResult result =  session.runRepGenp(symFile.getName(), queue, qtime, JobPrompts);
					tseq = result.getSeq();
				} else if (qtime != -1){
					SymitarSession.RunRepgenResult result =  session.runRepGenq(symFile.getName(), queue, qtime);
					tseq = result.getSeq();
				} else {
					SymitarSession.RunRepgenResult result =  session.runRepGen(symFile.getName(), queue);
					tseq = result.getSeq();
				}
				final int seq = tseq;
				//final int seq = result.getSeq();
				if( seq != -1) {
					log.info("Job started! " + "  name: " + jobName  + "  seq: " + seq );
					int retryDelay = 500; //in milliseconds
					while (session.isSeqRunning(seq)) {
						log.fine("# job " + seq + " still running... will check again in " + retryDelay + "ms");
						try { Thread.sleep(retryDelay); } catch (Exception e) {}
					}
					log.info("### Job Complete!  seq: " + seq + "   name: " + jobName);			
				}	
				else {
					log.severe("Job failed!  " + jobName + "  seq: " + seq);			
					System.exit(1);
				}	
            }
		}	
		catch (Exception e) {
			log.severe(e.getMessage());
			e.printStackTrace();
			System.exit(1);
		}
		
		System.exit(0);
	}
	
	
	private Properties loadConfig(String filePath) throws Exception {
		Properties config = new Properties();			
		File f = new File(filePath);
		log.fine("config file path: " + f.getAbsolutePath());				
		if(!f.exists()) {			
			throw new Exception("Config file not found: " + filePath);
		}			
		

		config.load(new FileReader(f));			
		return config;
	}	
	
	
}
