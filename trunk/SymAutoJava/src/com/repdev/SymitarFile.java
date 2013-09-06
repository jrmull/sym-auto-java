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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Serializable;
import java.util.Date;

/**
 * File on symitar server
 * @author Jake Poznanski
 *
 */
public class SymitarFile implements Serializable {
	private static final long serialVersionUID = 2L;
	
	private String name, dir = "";
	private FileType type;
	private Date modified = new Date(0), installed = new Date(0);
	private long size = -1;
	private boolean local = false, onDemand=false;
	private int sym;
	
	/**
	 * New Local file
	 * @param name
	 */
	public SymitarFile(String dir, String name){
		this.dir = dir;
		this.name = name;
		this.local = true;
		this.type = FileType.REPGEN;
	}
	
	public SymitarFile(String dir, String name, Date modified, long size){
		this.dir = dir;
		this.name = name;
		this.local = true;
		this.type = FileType.REPGEN;
		this.modified = modified;
		this.size = size;
	}
	
	public SymitarFile(int sym, String name, FileType type) {
		this.sym = sym;
		this.name = name;
		this.type = type;
	}
	
	public SymitarFile(String dir, String name, FileType type) {
		this.dir = dir;
		this.name = name;
		this.local = true;
		this.type = type;
	}

	public SymitarFile(int sym, String name, FileType type, Date modified, long size) {
		this.sym = sym;
		this.name = name;
		this.type = type;
		this.modified = modified;
		this.size = size;
	}
	
	public SymitarFile(int sym, String name, FileType type, Date modified, long size, boolean demand) {
		this.sym = sym;
		this.name = name;
		this.type = type;
		this.modified = modified;
		this.size = size;
		this.onDemand = demand;
	}
	
	public String getData(){
		if( !local ) {			
			return SymAutoJava.session.getFile(this);			
		}
		else{			
			StringBuilder sb= new  StringBuilder();
			try {
				BufferedReader in = new BufferedReader(new FileReader(getPath()));
				String line = "";				
				while( (line=in.readLine()) != null)
					sb.append(line + "\n");
				
				return sb.toString();
			} catch (FileNotFoundException e) {				
				return null;
			} catch (Exception e) {				
				return null;
			}
		}		
	}
	
	
	
	
	public boolean isLocal(){
		return local;
	}

	public int getSym() {
		return sym;
	}

	public void setSym(int sym) {
		this.sym = sym;
	}
	
	public String getName() {
		return name;
	}

	public FileType getType() {
		return type;
	}

	public String toString() {
		return name;
	}

	public Date getModified() {
		return modified;
	}

	public void setModified(Date modified) {
		this.modified = modified;
	}

	public long getSize() {
		return size;
	}

	public void setSize(long size) {
		this.size = size;
	}

	public boolean equals(Object o) {
		if (!(o instanceof SymitarFile))
			return false;

		SymitarFile file = (SymitarFile) o;
		return name.equals(file.name) && type.equals(file.type) && (isLocal() ? (dir != null && file.getDir() != null && file.getDir().equals(dir) ) : file.getSym() == sym);
	}

	public void setInstalled(Date installed) {
		this.installed = installed;
	}

	public Date getInstalled() {
		return installed;
	}
	
	public String getDir() {
		return dir;
	}
	
	public String getPath(){
		return dir + "\\" + name;
	}
	
	public boolean getOnDemand(){
		return onDemand;
	}
}
