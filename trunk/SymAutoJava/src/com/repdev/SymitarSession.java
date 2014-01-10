/** 
 *  RepDev - RepGen IDE for Symitar
 *  Copyright (C) 2007  Jake Poznanski, Ryan Schultz, Sean Delaney
 *  http://repdev.org/ <support@repdev.org>
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

import java.util.ArrayList;
import java.util.Collections;
 

/**
 * Abstract symitar session, allows for several different conneciton methods
 * @author Jake Poznanski  (Modified 8/29/2013 by Shane Morrell to accept port number as parameter in connect method.)   
 *
 */
public abstract class SymitarSession {
	protected String server, aixUsername, aixPassword, userID;
	protected int sym;
	

	/**
	 * Initiates a connection to the server, if we are not already connected
	 * 
	 * @param server
	 * @param aixUsername
	 * @param aixPassword
	 * @param sym
	 * @param userID
	 * @return SessionError
	 */
	public abstract SessionError connect(String server, int port, String aixUsername, String aixPassword, int sym, String userID);

	/**
	 * 
	 * @return SessionError
	 */
	public abstract SessionError disconnect();

	/**
	 * 
	 * @return boolean
	 */
	public abstract boolean isConnected();

	/**
	 * Gets file of given type from server
	 * 
	 * @param type
	 * @param name
	 * @return String containing contents, null if not connected or if can't
	 *         find file
	 */
	public abstract String getFile(SymitarFile file);

	public abstract boolean fileExists(SymitarFile file);

	public abstract SessionError removeFile(SymitarFile file);

	public abstract SessionError saveFile(SymitarFile file, String text);

	public abstract SessionError renameFile(SymitarFile file, String newName);

	public class RunRepgenResult{
		int seq, time;

		public RunRepgenResult(int seq, int time) {
			super();
			this.seq = seq;
			this.time = time;
		}

		public int getSeq() {
			return seq;
		}

		public void setSeq(int seq) {
			this.seq = seq;
		}

		public int getTime() {
			return time;
		}

		public void setTime(int time) {
			this.time = time;
		}		
	}



	public abstract RunRepgenResult runRepGen(String name, int queue);
	
	public abstract RunRepgenResult runRepGenq(String name, int queue, int qtime);
	
	public abstract RunRepgenResult runRepGenp(String name, int queue, int qtime, String JobPrompts);
	
	public abstract SessionError UnlockConsoles();
	
	/**
	 * Interface needed for runRepGen stuff, should maybe be it's own file later
	 */
	public interface PromptListener{
		String getPrompt(String name);
	}

	public abstract boolean isSeqRunning(int seq);

	public abstract void terminateRepgen(int seq);

	public class RunFMResult{
		private String resultTitle;
		private int seq;

		public RunFMResult(){
			setRandomTitle();
		}

		public RunFMResult(String resultTitle, int seq) {
			super();
			this.resultTitle = resultTitle;
			this.seq = seq;
		}

		private void setRandomTitle(){
			resultTitle = "RepDev FM - " + String.format("%06d", (int)(Math.random() * 10000000));
		}

		public String getResultTitle() {
			return resultTitle;
		}

		public void setResultTitle(String resultTitle) {
			this.resultTitle = resultTitle;
		}

		public int getSeq() {
			return seq;
		}

		public void setSeq(int seq) {
			this.seq = seq;
		}		
	}

	public enum FMFile{
		ACCOUNT("Account"),
		INVENTORY("Inventory"),
		PAYEE("Payee"),
		GL_ACCOUNT("GL_Account"),
		RECIEVED_ITEM("Recieved_Item"),
		PARTICIPANT("Partipant"),
		PARTICIPATION("Participation"),
		DEALER("Dealer"),
		USER("User"),
		COLLATERAL("Collateral");

		private String displayName;

		private FMFile(String displayName){
			this.displayName = displayName;
		}

		public String getDisplayName()
		{
			return displayName;
		}
	}

	public abstract RunFMResult runBatchFM(String searchTitle, int searchDays, FMFile file, int queue);

	

	/**
	 * Goes through past several batch output files in print control
	 * If certain time is given, just returns that one, 
	 * if it's -1, then finds last couple
	 * 
	 * The time specifier is important for the following reason:
	 * If two instance of a samed named repgen are being run at the same time (usually long reports),
	 * we only want to pick the one that we started ourselves. This option is currently
	 * only used by the Run Report feature.
	 * 
	 * @param reportName
	 * @param time
	 * @param limit
	 * @return
	 */


	
	/**
	 * Supports default "+" as a wildcard
	 * 
	 * @param search
	 * @return
	 */
	public abstract ArrayList<SymitarFile> getFileList(FileType type, String search);

	public abstract SessionError printFileLPT(SymitarFile file, int queue, boolean formsOverride, int formLength, int startPage, int endPage, int copies, boolean landscape, boolean duplex, int queuePriority);

	/**
	 * Calls the regular print command with default options
	 */
	public SessionError printFileLPT(SymitarFile file, int queue) {
		return printFileLPT(file, queue, false, 0, 0, 0, 1, false, false, 4);
	}

	public abstract SessionError printFileTPT(SymitarFile file, int queue);

	public abstract ErrorCheckResult errorCheckRepGen(String filename);

	public String getAixUsername() {
		return aixUsername;
	}

	public String getAixPassword() {
		return aixPassword;
	}

	public int getSym() {
		return sym;
	}

	public String getServer() {
		return server;
	}

	public String getUserID() {
		return userID;
	}

	public abstract ErrorCheckResult installRepgen(String f);

}
