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



public enum SessionError {
	NONE, SERVER_NOT_FOUND, AIX_LOGIN_WRONG, SYM_INVALID, USERID_INVALID, ALREADY_CONNECTED, NOT_CONNECTED, IO_ERROR, CONSOLE_BLOCKED,
	INVALID_FILE_TYPE, INVALID_QUEUE, INPUT_ERROR, FILENAME_TOO_LONG, ARGUMENT_ERROR, NULL_POINTER, UNLOCK_CONSOLE_ERROR;
	
	public void showError(){
		
	}
}; 