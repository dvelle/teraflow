/*
 * Copyright 2019 Terazyte
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.terazyte.flow.task.common

import java.io.{File, PrintWriter}
import java.nio.file.{Files, Paths}
import java.util.Date

import scala.sys.process._

object FileHelper {

  def getLocalScriptPath(): String = {
    val userDir   = System.getProperty("user.home")
    val localPath = userDir + slash + ".launcher"
    Files.createDirectories(Paths.get(localPath))
    localPath + slash + new Date().getTime.toString + ".sh"
  }

  def writeTo(script: String, path: Option[String] = None): String = {
    val localPath = path match {
      case Some(p) => p
      case None =>
        val userDir = System.getProperty("user.home")
        userDir + slash + ".launcher"
    }

    Files.createDirectories(Paths.get(localPath))
    val completePath = localPath + slash + new Date().getTime.toString + ".sh"
    val pw           = new PrintWriter(new File(completePath))
    pw.write(script)
    pw.close

    //Assign the permission
    completePath
  }

  def setExecutable(path: String): Int = {
    val p = s"chmod +x ${path}".run()
    p.exitValue()
  }

  def slash: String = "/"

}
