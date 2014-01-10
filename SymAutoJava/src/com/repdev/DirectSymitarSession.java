/** 
 *  RepDev - RepGen IDE for Symitar
 *  Copyright (C) 2007  Jake Poznanski, Ryan Schultz, Sean Delaney
 *  http://repdev.org/ <support@repdev.org>
 *
 *  Modified 8/12/13 by Shane Morrell shanem@thcontact.com
 *
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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.logging.ConsoleHandler;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;



/**
 * This is the main connection object to the Symitar host, it provides all the routines you would need to connect
 * 
 * Provides classes and methods also to help in reading commands from the server, parsing them, creating new ones, etc.
 * 
 * @author Jake Poznanski
 *
 */
public class DirectSymitarSession extends SymitarSession {
	private static final Logger log = Logger.getLogger( DirectSymitarSession.class.getName() );

	Socket socket;
	BufferedReader in;
	PrintWriter out;
	boolean connected = false;
	Thread keepAlive;


	public SessionError connect(String server, int port, String aixUsername, String aixPassword, int sym, String userID) {
		String line = "";

		if( connected )
			return SessionError.ALREADY_CONNECTED;
		
		this.sym = sym;
		this.server = server;
		this.aixUsername = aixUsername;
		this.aixPassword = aixPassword;
		this.userID = userID;
		final int tmpSym = this.sym;

		try {
			socket = new Socket(server, port);
			socket.setKeepAlive(true);
		
			// Constant commands, these are the basic telnet establishment
			// stuffs, which really don't change, so I just send them directly
			char init1[] = { 0xff, 0xfb, 0x18 };
			char init2[] = { 0xff, 0xfa, 0x18, 0x00, 0x61, 0x69, 0x78, 0x74, 0x65, 0x72, 0x6d, 0xff, 0xf0 };
			char init3[] = { 0xff, 0xfd, 0x01 };
			char init4[] = { 0xff, 0xfd, 0x03, 0xff, 0xfc, 0x1f, 0xff, 0xfc, 0x01 };

			in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
			out = new PrintWriter(socket.getOutputStream());

			out.print(init1);
			out.print(init2);
			out.print(init3);
			out.print(init4);
			out.print(aixUsername + "\r");
			out.flush();

			String temp = readUntil("Password:", "[c");
		
			if( temp.indexOf("[c") == -1 ){
				line = writeLog(aixPassword + "\r", "[c", ":");
	
				if (line.indexOf("invalid login") != -1){
					disconnect();
					return SessionError.AIX_LOGIN_WRONG;
				}
			}

			write("WINDOWSLEVEL=3\n");
			
			temp = readUntil( "$ ", "SymStart~Global");
			
			log.info(temp);
			
			
			if( temp.contains("$ ") )
				write("sym " + sym + "\r");

			Command current;

			while (!(current = readNextCommand()).getCommand().equals("Input") || current.getParameters().get("HelpCode").equals("10025")){
				log.info(current.toString());
				if(current.getCommand().equals("Input") && current.getParameters().get("HelpCode").equals("10025")){
					write("$WinHostSync$\r");
					log.info("HelpCode 10025 found ! ! !\n");
				}

				if( current.getCommand().equals("SymLogonError") && current.getParameters().get("Text").contains("Too Many Invalid Password Attempts") ){
					disconnect();
					return SessionError.CONSOLE_BLOCKED;
				}
			}

			log.info(current.toString());

			write(userID + "\r");

			current = readNextCommand();
			log.info("USER RESPONSE: " + current.getCommand());
			
			if (current.getCommand().equals("SymLogonInvalidUser")) {
				log.info("Bad password");
			}

			write("\r");
			readNextCommand();
			
			write("\r");
			log.info(readNextCommand().toString());
			
			connected = true;
			log.info("Connected to Symitar!");			
						
		} catch (UnknownHostException e) {
			e.printStackTrace();
			disconnect();
			return SessionError.SERVER_NOT_FOUND;
		} catch (IOException e) {
			e.printStackTrace();
			disconnect();
			return SessionError.IO_ERROR;
		}

		return SessionError.NONE;
	}

	private static class Command {
		String command = "";
		HashMap<String, String> parameters = new HashMap<String, String>();
		String data = "";
		static Pattern commandPattern = Pattern.compile("(.*?)~.*");
		static int currentMessageId = 10000;

		public Command(String command) {
			parameters.put("MsgId", String.valueOf(currentMessageId));
			currentMessageId++;

			this.command = command;
		}

		@SuppressWarnings("unused")
		public Command(String command, HashMap<String, String> parameters, String data) {
			super();
			this.command = command;
			this.parameters = parameters;
			this.data = data;

			parameters.put("MsgId", String.valueOf(currentMessageId));
			currentMessageId++;
		}

		public Command() {
			parameters.put("MsgId", String.valueOf(currentMessageId));
			currentMessageId++;
		}

		// Returns string containing any file data sent in this message
		@SuppressWarnings("unused")
		public String getFileData() {
			
			if( data.indexOf(Character.toString((char) 253)) != -1 && data.indexOf(Character.toString((char) 254)) != -1)
				return data.substring(data.indexOf(Character.toString((char) 253)) + 1, data.indexOf(Character.toString((char) 254)));
			else
				return "";
		}

		public String sendStr() {
			String data = "";
			data += command + "~";

			for (String key : parameters.keySet())
				if (parameters.get(key).equals(""))
					data += key + "~";
				else
					data += key + "=" + parameters.get(key) + "~";

			data = data.substring(0, data.length() - 1);
			log.fine(Character.toString((char) 0x07) + data.length() + "\r" + data);
			return Character.toString((char) 0x07) + data.length() + "\r" + data;
		}

		public static Command parse(String data) {
			String[] sep;

			Command command = new Command();
			command.setData(data);

			if (data.indexOf("~") != -1 && data.indexOf(253) == -1) {
				Matcher match;
				match = commandPattern.matcher(data);
				match.matches();

				command.setCommand(match.group(1));
				sep = data.substring(match.group(1).length() + 1).split("~");

				for (String cur : sep) {
					if (cur.indexOf("=") == -1)
						command.getParameters().put(cur, "");
					else
						command.getParameters().put(cur.substring(0, cur.indexOf("=")), cur.substring(cur.indexOf("=") + 1));
				}
			} else
				command.setCommand(data);

			return command;
		}

		public String toString() {
			return data;
		}

		public String getCommand() {
			return command;
		}

		public void setCommand(String command) {
			this.command = command;
		}

		public String getData() {
			return data;
		}

		public void setData(String data) {
			this.data = data;
		}

		public HashMap<String, String> getParameters() {
			return parameters;
		}

		public void setParameters(HashMap<String, String> parameters) {
			this.parameters = parameters;
		}
	}

	private void write(String str) {
		out.write(str);
		out.flush();
	}

	private void write(Command cmd) {
		write(cmd.sendStr());
	}

	private String writeLog(String command, String... waitFor) throws IOException {
		write(command);
		String s = readUntil(waitFor);
		log.info(s);
		return s;
	}

	private Command readNextCommand() throws IOException {
		readUntil(Character.toString((char) 0x1b) + Character.toString((char) 0xfe));
		String data = readUntil(Character.toString((char) 0xfc));

		Command cmd = Command.parse(data.substring(0, data.length() - 1));
		
		//Filter out Messages that come in asychronously and fuck everything up
		if( cmd.getCommand().equals("MsgDlg") && cmd.getParameters().get("Text").contains("From PID") )
			return readNextCommand();
		else
			//TODO:Fix logons for syms that require password updates,
			//there is interception here for finding if symitar wants the password changed
			//but this might not be the ideal place to do it
			if ( cmd.toString().indexOf("User Password NOT changed") != -1){
				log.info("Please update your password");		//I added in this brief explination to help people
			}

			return cmd;
	
	}

	private String readUntil(String... strs) throws IOException {
		String buf = "";

		while (true) {
			int cur = in.read();
			buf += (char) cur;
			for (String str : strs)
				if (buf.indexOf(str) != -1)
					return buf;
		}
	}

	@Override
	public SessionError disconnect() {
		try {
			if( keepAlive != null)
				keepAlive.interrupt();
			
			if( in != null)
				in.close();
			
			if( out != null)
				out.close();
			
			if( socket != null)
				socket.close();
		} catch (Exception e) {
			return SessionError.IO_ERROR;
		}
		connected = false;
		return SessionError.NONE;
	}

	private synchronized void wakeUp(){
		write(new Command("WakeUp"));
	}
	
	@Override
	public synchronized ErrorCheckResult errorCheckRepGen(String filename) {				
		return null;
	}

	@Override
	public synchronized boolean fileExists(SymitarFile file){
		return getFileList(file.getType(), file.getName()).size() > 0;
	}
	
	@Override
	public synchronized String getFile(SymitarFile file) {		
			return null;
	}

	@Override
	public synchronized ArrayList<SymitarFile> getFileList(FileType type, String search) {		
		return null;
	}


	@Override
	public boolean isConnected() {
		return connected;
	}

	//TODO: Add more error checking
	@Override
	public SessionError printFileLPT(SymitarFile file, int queue, boolean formsOverride, int formLength, int startPage, int endPage, int copies, boolean landscape, boolean duplex, int queuePriority) {				
		return null;
	}

	@Override
	public SessionError printFileTPT(SymitarFile file, int queue) {		
		return null;
	}

	
	@Override
	public SessionError removeFile(SymitarFile file) {
		return null;
	}

	@Override
	public synchronized RunFMResult runBatchFM(String searchTitle, int searchDays, FMFile file, int queue) {
		return null;
	}
	
	

	
	/**
	 * Queue, -1 for first available,
	 * any other number for a specific one
	 * 
	 */
	public synchronized RunRepgenResult runRepGen(String name, int queue) {
		log.fine("#  runRepGen(" + name + ", " + queue + ")");
		
		Command cur;
		int[] queueCounts = new int[10000];
		boolean[] queueAvailable = new boolean[10000];
		int seq = -1, time = 0;
		
		//We cannot use queueCounts as an availbility thing, though it would be nice
		//The two arrays are parsed seperately, queueCounts from the list of queus and wahts in them
		//queueAvailable is from a seperate request saying which ones can actually run repwriters		
		for( int i = 0; i < queueCounts.length; i++) {
			queueCounts[i] = -1;
		}		
		
		try{			
			//Main Menu
			log.fine("#  send: mm0 (batch)");
			write("mm0" + (char)27);  //Batch
			while( !(cur = readNextCommand()).getCommand().equals("Input") ) {}
			log.fine(cur.toString());
			
			// Job File							
			log.fine("#  send: 0 (Run Job File)");
			writeAndWaitForInputPrompt("0\r");  //Run Job FIle
			
			// Job Name
			log.fine("#  send: job name " + name);
			write(name + "\r");  //job name
			while( !(cur = readNextCommand()).getCommand().equals("Input") ) {
				String command = cur.getCommand();
				String type = cur.getParameters().get("Type");
				String text = cur.getParameters().get("Text");				
				if ("Error".equals(type)) {
					log.severe("Job " + name + " failed!  " + text);
					return new RunRepgenResult(-1,0);
				}
				log.fine(cur.toString());
			}
			log.fine(cur.toString()); 
		
			
			log.fine("#  send: Y");
			writeAndWaitForInputPrompt("Y\r");  //batch options
						
			log.fine("#  send: Y");
			writeAndWaitForInputPrompt("Y\r");  //notify on completion
						
			log.fine("#  send: 1");
			writeAndWaitForInputPrompt("1\r");  //queue priority
						
			log.fine("#  send: return");
			writeAndWaitForInputPrompt("\r");  //start date
			
			log.fine("#  send: return");			
			write( "\r" );  //Start Time
			while( !(cur = readNextCommand()).getCommand().equals("Input") ){
				log.fine(cur.toString());
				
				//Determine which batch queues are available
				if( cur.getParameters().get("Action").equals("DisplayLine") && cur.getParameters().get("Text").contains("Batch Queues Available:")){					
					String line = cur.getParameters().get("Text");
					String[] tempQueues = line.substring(line.indexOf(":") + 1).split(",");
					log.fine("#  queues avail: " + java.util.Arrays.toString(tempQueues)  + "  length: " + tempQueues.length);
					
					int i = 0;
					for( String temp : tempQueues){
						temp = temp.trim();
						
						if( temp.contains("-"))
						{
							String[] tempList = temp.split("-");
							
							int start = Integer.parseInt(tempList[0].trim());
							int end = Integer.parseInt(tempList[1].trim());
							
							for( int x = start; x <= end; x++){
								queueAvailable[x]=true;
							}
						}
						else
						{
							queueAvailable[Integer.parseInt(temp)] = true;
						}
						
						i++;
					}			
				}
			}
			log.fine(cur.toString());			
						

			log.fine("#  send: queue " + queue);
			//If queue was given, send it, otherwise just send a return
			if (queue > -1) {  
				write(Integer.toString(queue));  
			}
			writeAndWaitForInputPrompt("\r");   //Batch Queue
			
			
			log.fine("#  send: Y");
			writeAndWaitForInputPrompt("Y\r");  //Okay?

			
			//Find the job in the batch queue and grab the seq number for later use			
			int newestTime = 0;
			Command getQueues = new Command("Misc");
			getQueues.getParameters().put("InfoType", "BatchQueues");
			log.fine("# send getQueues");
			write(getQueues);
			while( (cur = readNextCommand()).getParameters().get("Done") == null ){
								
				//Get the Sequence for the latest running one at this point, and return it so we can keep track of it
				if( cur.getParameters().get("Action").equals("QueueEntry") ){
					log.fine(cur.toString());
					int curTime = 0;
					String timeStr = cur.getParameters().get("Time");
					curTime = Integer.parseInt(timeStr.substring(timeStr.lastIndexOf(":")+1));
					curTime += 60 * Integer.parseInt(timeStr.substring(timeStr.indexOf(":")+1, timeStr.lastIndexOf(":")));
					curTime += 3600 * Integer.parseInt(timeStr.substring(0,timeStr.indexOf(":")));
					
					if( curTime > newestTime )
					{
						newestTime = curTime;
						seq = Integer.parseInt(cur.getParameters().get("Seq"));
						time = curTime;
					}
				}
			}			
		}
		catch(Exception e){
			e.printStackTrace();
			log.severe("FAIL!  " +  e.getMessage());
			return new RunRepgenResult(-1,0);
		}		
		
		return new RunRepgenResult(seq,time);
	}
	
	/**
     * runRepGenq()
     * Similar to runRepGen but with added qtime to handle additional scheduled time
     * Matt Warren
     */
	public synchronized RunRepgenResult runRepGenq(String name, int queue, int qtime) {
   
		log.fine("#  runRepGenq(" + name + ", " + queue + ", " + qtime + ")");
		
		Command cur;
		int[] queueCounts = new int[10000];
		boolean[] queueAvailable = new boolean[10000];
		int seq = -1, time = 0;
		
		//We cannot use queueCounts as an availability thing, though it would be nice
		//The two arrays are parsed separately, queueCounts from the list of queues and whats' in them
		//queueAvailable is from a separate request saying which ones can actually run repwriters		
		for( int i = 0; i < queueCounts.length; i++) {
			queueCounts[i] = -1;
		}		
		
		try{			
			//Main Menu
			log.fine("#  send: mm0 (batch)");
			write("mm0" + (char)27);  //Batch
			while( !(cur = readNextCommand()).getCommand().equals("Input") ) {}
			log.fine(cur.toString());
			
			// Job File							
			log.fine("#  send: 0 (Run Job File)");
			writeAndWaitForInputPrompt("0\r");  //Run Job FIle
			
			// Job Name
			log.fine("#  send: job name " + name);
			write(name + "\r");  //job name
			while( !(cur = readNextCommand()).getCommand().equals("Input") ) {
				String command = cur.getCommand();
				String type = cur.getParameters().get("Type");
				String text = cur.getParameters().get("Text");				
				if ("Error".equals(type)) {
					log.severe("Job " + name + " failed!  " + text);
					return new RunRepgenResult(-1,0);
				}
				log.fine(cur.toString());
			}
			log.fine(cur.toString()); 
		
			
			log.fine("#  send: Y");
			writeAndWaitForInputPrompt("Y\r");  //batch options
						
			log.fine("#  send: Y");
			writeAndWaitForInputPrompt("Y\r");  //notify on completion
						
			log.fine("#  send: 4");
			writeAndWaitForInputPrompt("4\r");  //queue priority
						
			log.fine("#  send: return");
			writeAndWaitForInputPrompt("\r");  //start date
			
			log.fine("#  send: "+ String.format("%04d", qtime));			
			write( String.format("%04d", qtime) + "\r" );  //Start Time
						
			while( !(cur = readNextCommand()).getCommand().equals("Input") ){
				log.fine(cur.toString());
				
				//Determine which batch queues are available
				if( cur.getParameters().get("Action").equals("DisplayLine") && cur.getParameters().get("Text").contains("Batch Queues Available:")){					
					String line = cur.getParameters().get("Text");
					String[] tempQueues = line.substring(line.indexOf(":") + 1).split(",");
					log.fine("#  queues avail: " + java.util.Arrays.toString(tempQueues)  + "  length: " + tempQueues.length);
					
					for( String temp : tempQueues){
						temp = temp.trim();
						
						if( temp.contains("-"))
						{
							String[] tempList = temp.split("-");
							
							int start = Integer.parseInt(tempList[0].trim());
							int end = Integer.parseInt(tempList[1].trim());
							
							for( int x = start; x <= end; x++){
								queueAvailable[x]=true;
							}
						}
						else
						{
							queueAvailable[Integer.parseInt(temp)] = true;
						}
						
					}			
				}
			}
			log.fine(cur.toString());			
						

			log.fine("#  send: queue " + queue);
			//If queue was given, send it, otherwise just send a return
			if (queue > -1) {  
				write(Integer.toString(queue));  
			}
			writeAndWaitForInputPrompt("\r");   //Batch Queue
			
			log.fine("#  send: return");
			writeAndWaitForInputPrompt("\r");  //Expected System Date
			
			log.fine("#  send: return");
			writeAndWaitForInputPrompt("\r");  //Expected Previous System Date
			
			log.fine("#  send: Y");
			writeAndWaitForInputPrompt("Y\r");  //Okay?

			
			//Find the job in the batch queue and grab the seq number for later use			
			int newestTime = 0;
			Command getQueues = new Command("Misc");
			getQueues.getParameters().put("InfoType", "BatchQueues");
			log.fine("# send getQueues");
			write(getQueues);
			while( (cur = readNextCommand()).getParameters().get("Done") == null ){
								
				//Get the Sequence for the latest running one at this point, and return it so we can keep track of it
				if( cur.getParameters().get("Action").equals("QueueEntry") ){
					log.fine(cur.toString());
					if( cur.getParameters().get("Stat").equals("Scheduled") ){
						//no seq can be given since we are scheduling the job, default to 9999
						seq = 9999;
						int aTime = 0;
						String timeStr = cur.getParameters().get("AfterTime");
						aTime = Integer.parseInt(timeStr.substring(timeStr.lastIndexOf(":")+1));
						aTime += 60 * Integer.parseInt(timeStr.substring(timeStr.indexOf(":")+1, timeStr.lastIndexOf(":")));
						aTime += 3600 * Integer.parseInt(timeStr.substring(0,timeStr.indexOf(":")));
						time = aTime;
					}
					else {
						int curTime = 0;
						String timeStr = cur.getParameters().get("Time");
						curTime = Integer.parseInt(timeStr.substring(timeStr.lastIndexOf(":")+1));
						curTime += 60 * Integer.parseInt(timeStr.substring(timeStr.indexOf(":")+1, timeStr.lastIndexOf(":")));
						curTime += 3600 * Integer.parseInt(timeStr.substring(0,timeStr.indexOf(":")));
						
						if( curTime > newestTime )
						{
							newestTime = curTime;
							seq = Integer.parseInt(cur.getParameters().get("Seq"));
							time = curTime;
						}
					}
				}
			}			
		}
		catch(Exception e){
			e.printStackTrace();
			log.severe("FAIL!  " +  e.getMessage());
			return new RunRepgenResult(-1,0);
		}		
		
		return new RunRepgenResult(seq,time);
	}
	
	/**
     * runRepGenp()
     * Matt Warren
     * Similar to runRepGenq but with added JobPrompts to handle additional prompts that a job may request input for.
     * MUST BE EXACT Job prompts to match!  Prompts cannot be variable.  
     * Prompts and their values cannot have quotes("), pipes(|) or equal signs(=) in them.
     * Each prompt needs to be separated by the | character.  Prompts must follow in succession as they are sent from the host.
     * example JobPrompt arg with three prompts:
     *   Enter Date
     *   ATM Settlement Date
     *   Select option: default [2]
     *   "Enter Date=12312013|ATM Settlement Date=!SYSTEMDATE-2|Select option: default [2]=1"
     * 
     * queue and qtime are required arguments but can be -1.
     */
	public synchronized RunRepgenResult runRepGenp(String name, int queue, int qtime, String JobPrompts) {
   
		log.info("#  runRepGenp(" + name + ", " + queue + ", " + qtime + ", " + JobPrompts +")");
		
		Command cur;
		int[] queueCounts = new int[10000];
		boolean[] queueAvailable = new boolean[10000];
		int seq = -1, time = 0;
		String[] JobDesc = new String[100];
		String[] JobValue = new String[100];
		String[] Prompts = new String[100];
		String[] TPrompts = new String[1];
		if (JobPrompts.contains("|")){
			Prompts = JobPrompts.split("\\|");
		} else {
			TPrompts[0] = JobPrompts;
			Prompts = TPrompts;
		}
		
		
		if (Prompts.length > 0) {
			String[] tmpJob = new String[2];
			String[] TtmpJob = new String[2];
			//split out the prompt names/values found in array
			//log.info("Prompts Length:" + String.format("%03d", Prompts.length));
			for( int x = 0;  x < Prompts.length; x++){
				//log.info("Prompt:"+Prompts[x]+" at index:" + x);
				if (!(Prompts[x].equals("")) && !(Prompts[x].equals(null))){
					tmpJob = Prompts[x].split("=");
					
					//log.info("tmpJob Length:" +String.format("%03d", tmpJob.length));
					if (tmpJob.length == 1){
						TtmpJob[0] = Prompts[x].substring(0, Prompts[x].lastIndexOf("="));
						TtmpJob[1] = null;
						tmpJob = TtmpJob;
					}
					
					JobDesc[x] = tmpJob[0];
					JobValue[x] = tmpJob[1];
					//log.info("At Prompt:"+JobDesc[x]+" Enter value: "+JobValue[x] + " index:" + x);
				}
				
			}
		}
		
		
		//We cannot use queueCounts as an availability thing, though it would be nice
		//The two arrays are parsed separately, queueCounts from the list of queues and whats' in them
		//queueAvailable is from a separate request saying which ones can actually run repwriters		
		for( int i = 0; i < queueCounts.length; i++) {
			queueCounts[i] = -1;
		}		
		
		try{			
			//Main Menu
			log.fine("#  send: mm0 (batch)");
			write("mm0" + (char)27);  //Batch
			while( !(cur = readNextCommand()).getCommand().equals("Input") ) {}
			log.info(cur.toString());
			
			// Job File							
			log.fine("#  send: 0 (Run Job File)");
			writeAndWaitForInputPrompt("0\r");  //Run Job FIle
			
			// Job Name
			log.fine("#  send: job name " + name);
			write(name + "\r");  //job name
			while( !(cur = readNextCommand()).getCommand().equals("Input") ) {
				String command = cur.getCommand();
				String type = cur.getParameters().get("Type");
				String text = cur.getParameters().get("Text");				
				if ("Error".equals(type)) {
					log.fine("Job " + name + " failed!  " + text);
					return new RunRepgenResult(-1,0);
				}
				log.fine(cur.toString());
			}
			log.fine(cur.toString()); 
		
			
			log.fine("#  send: Y");
			writeAndWaitForInputPrompt("Y\r");  //batch options
						
			log.fine("#  send: Y");
			writeAndWaitForInputPrompt("Y\r");  //notify on completion
						
			log.fine("#  send: 4");
			writeAndWaitForInputPrompt("4\r");  //queue priority
						
			log.fine("#  send: return");
			writeAndWaitForInputPrompt("\r");  //start date
			
			if (qtime > -1) {
				log.fine("#  send: "+ String.format("%04d", qtime));			
				write( String.format("%04d", qtime) );  //Start Time
			}
			write("\r");
			
			while( !(cur = readNextCommand()).getCommand().equals("Input") ){
				log.fine(cur.toString());
				
				//Determine which batch queues are available
				if( cur.getParameters().get("Action").equals("DisplayLine") && cur.getParameters().get("Text").contains("Batch Queues Available:")){					
					String line = cur.getParameters().get("Text");
					String[] tempQueues = line.substring(line.indexOf(":") + 1).split(",");
					log.fine("#  queues avail: " + java.util.Arrays.toString(tempQueues)  + "  length: " + tempQueues.length);
					
					for( String temp : tempQueues){
						temp = temp.trim();
						
						if( temp.contains("-"))
						{
							String[] tempList = temp.split("-");
							
							int start = Integer.parseInt(tempList[0].trim());
							int end = Integer.parseInt(tempList[1].trim());
							
							for( int x = start; x <= end; x++){
								queueAvailable[x]=true;
							}
						}
						else
						{
							queueAvailable[Integer.parseInt(temp)] = true;
						}
						
					}			
				}
			}
			//log.fine(cur.toString());			
						

			log.fine("#  send: queue " + queue);
			//If queue was given, send it, otherwise just send a return
			if (queue > -1) {  
				write(Integer.toString(queue));  
			}
			
			if (qtime > -1) {
				writeAndWaitForInputPrompt("\r");   //Batch Queue
				log.fine("#  send: return");
				writeAndWaitForInputPrompt("\r");  //Expected System Date
				
				log.fine("#  send: return");
				write("\r"); //Expected Previous System Date
			}
			else {
				write("\r");   //Batch Queue
			}
			 
			
			//log.fine(cur.toString());
			while( (cur = readNextCommand()).getParameters().get("Prompt") == null ){
				log.fine(cur.toString());
			}
			
			//log.fine("Prompt Length:"+String.format("%04d", Prompts.length));
			//log.fine(cur.toString());
			for( int x = 0; x < Prompts.length; x++){ //JobDesc[x] != null
				//log.fine("x="+ String.format("%01d", x) + " " + JobDesc[x]);
				if( cur.getParameters().get("Prompt").equals(JobDesc[x]) ){
					//log.fine(JobDesc[x]+"|"+JobValue[x]);
					if ((JobValue[x] != null)){
						//log.fine("#  send: "+ JobValue[x]);
						write(JobValue[x]+"\r");
					} else {
						//log.fine("#  send: nothing "+ JobValue[x]);
						write("\r");
					}
				}
				while( (cur = readNextCommand()).getParameters().get("Prompt") == null ){
					log.fine(cur.toString());
				}
			}	
			
			
			
			log.fine("#  send: Y");
			writeAndWaitForInputPrompt("Y\r");  //Okay?

			
			//Find the job in the batch queue and grab the seq number for later use			
			int newestTime = 0;
			Command getQueues = new Command("Misc");
			getQueues.getParameters().put("InfoType", "BatchQueues");
			log.fine("# send getQueues");
			write(getQueues);

			while( !(cur.getData().contains("~Done"))){ 
				//Get the Sequence for the latest running one at this point, and return it so we can keep track of it
				log.fine(cur.toString()+" - In while");
				if (qtime > -1) {
					if(cur.getData().contains("Job="+name)){
						//log.info(cur.toString()+" - In if");
						if( cur.getData().contains("Stat=Scheduled")){
							//no seq can be given since we are scheduling the job, default to 9999
							
							seq = 9999; 
							int aTime = 0;
							String timeStr = cur.getParameters().get("AfterTime");
							aTime = Integer.parseInt(timeStr.substring(timeStr.lastIndexOf(":")+1));
							aTime += 60 * Integer.parseInt(timeStr.substring(timeStr.indexOf(":")+1, timeStr.lastIndexOf(":")));
							aTime += 3600 * Integer.parseInt(timeStr.substring(0,timeStr.indexOf(":")));
							time = aTime;
						}
						else {
							int curTime = 0;
							String timeStr = cur.getParameters().get("Time");
							curTime = Integer.parseInt(timeStr.substring(timeStr.lastIndexOf(":")+1));
							curTime += 60 * Integer.parseInt(timeStr.substring(timeStr.indexOf(":")+1, timeStr.lastIndexOf(":")));
							curTime += 3600 * Integer.parseInt(timeStr.substring(0,timeStr.indexOf(":")));
							
							if( curTime > newestTime )
							{
								newestTime = curTime;
								seq = Integer.parseInt(cur.getParameters().get("Seq"));
								time = curTime;
							}
						}
					}
				} else {
					if (queue == -1){
						queue = 0;
					}
					if( cur.getData().contains("Stat=Running") && cur.getData().contains("Queue="+Integer.toString(queue))){
						int curTime = 0;
						String timeStr = cur.getParameters().get("Time");
						curTime = Integer.parseInt(timeStr.substring(timeStr.lastIndexOf(":")+1));
						curTime += 60 * Integer.parseInt(timeStr.substring(timeStr.indexOf(":")+1, timeStr.lastIndexOf(":")));
						curTime += 3600 * Integer.parseInt(timeStr.substring(0,timeStr.indexOf(":")));
						
						if( curTime > newestTime )
						{
							newestTime = curTime;
							seq = Integer.parseInt(cur.getParameters().get("Seq"));
							time = curTime;
						}
					}
				}
				
				cur = readNextCommand();
			}
			
		}
		catch(Exception e){
			e.printStackTrace();
			log.severe("FAIL!  " +  e.getMessage());
			return new RunRepgenResult(-1,0);
		}		
		
		return new RunRepgenResult(seq,time);
	}
	
	
	 /**
     * UnlockConsoles()
     * Clear all the locked consoles
     * Josh Marshall
     * Matt Warren
     */
    public synchronized SessionError UnlockConsoles() {
    log.fine("#  UnlockConsoles");
    Command cur;
    int seq = -1, time = 0;
    String tmpConsole=""; 
    String tmpString="";
        try{                    
            //Main Menu
            log.fine("#  send: mm6 (Console Control)");
            write("mm6" + (char)27);  //Console control
            while( !(cur = readNextCommand()).getCommand().equals("Input") ) {}
            writeAndWaitForInputPrompt("4\r");  //open to security
            
            //while( !(cur = readNextCommand()).getCommand().equals("Input") ) {}
            log.fine(cur.toString());
           
            writeAndWaitForInputPrompt( "\r" );
            write("1\r");  // reset Locked Console
            //write("\r");  
            cur = readNextCommand();
            log.fine(cur.toString());
        	log.fine("#  send: looking for locked consoles");
            while( !(cur.getCommand().equals("Input") )) 
            {
            	log.fine(cur.toString());
				  //get the locked consoles unlock them
				
				  if (cur.getCommand().equals("SecControl"))
				  {
					  if (cur.getData().contains("ConListItem"))
					  {
						  tmpString = cur.getData();//Get the data from the command
						  
						  //Get the console numbers from the data and separate with comma if multiple found
						  if (tmpConsole != ""){
							  tmpConsole = tmpConsole + "," + tmpString.substring(tmpString.indexOf("~Con=")+5, tmpString.indexOf("~ConName"));
						  } else {
							  tmpConsole = tmpString.substring(tmpString.indexOf("~Con=")+5, tmpString.indexOf("~ConName"));
						  }
					  }

				  }
				  cur = readNextCommand();
            }
            log.fine(cur.toString());
            
            //split out the consoles found into array
            List<String> items = Arrays.asList(tmpConsole.split(","));
            
            //parse through each and unlock
            for (String s : items){
            	if (!(s.equals("")))
                {
    				  //Console#[0x0d]  
    				  log.fine("#  send: unlock " + s);
    				  //log.info(cur.toString());
    				  writeAndWaitForInputPrompt(s + "\r");//send command back to unlock given console
    				  //log.info(cur.toString());
    				  writeAndWaitForInputPrompt("0\r");//ok
    				  //log.info(cur.toString());
                }
            }
        }
        catch(Exception e){
                e.printStackTrace();
                log.severe("FAIL!  " +  e.getMessage());
                return SessionError.UNLOCK_CONSOLE_ERROR;
        }              
       
        return SessionError.NONE;
    }
    
	private void writeAndWaitForInputPrompt(String s) {		
		//log.info("#  writeAndWaitForInputResponse: " + s);
		try {
			write(s);
			Command cur = null;
			while( !(cur = readNextCommand()).getCommand().equals("Input") )
				log.fine(cur.toString());
			log.fine(cur.toString());
		}
		catch(Exception e) {  e.printStackTrace(); }
	}
	
	

	@Override
	public synchronized SessionError saveFile(SymitarFile file, String text) {	
		return SessionError.NONE;
	}

	@Override
	public synchronized ErrorCheckResult installRepgen(String filename) {		
		return null;
	}

	@Override
	/**
	 * Remember that batch queue sequence numbers are not related to print queue ones!
	 */
	public synchronized boolean isSeqRunning(int seq) {
		Command cur;
		boolean running = false;
		
		if( !connected )
			return false;
		
		//Batch queue selection
		Command getQueues = new Command("Misc");
		getQueues.getParameters().put("InfoType", "BatchQueues");
		write(getQueues);
		
		try{
			while( (cur = readNextCommand()).getParameters().get("Done") == null ){
				if( cur.getParameters().get("Action").equals("QueueEntry") && Integer.parseInt(cur.getParameters().get("Seq"))==seq) {
					log.info(cur.toString());				
					running = true;
				}
			}			
		}
		catch(IOException e){
			return false;
		}
		
		return running;
	}
	
	

	@Override
	public void terminateRepgen(int seq) {	
	}

		
	@Override
	public synchronized SessionError renameFile(SymitarFile file, String newName) {
		return null;
	}

}
