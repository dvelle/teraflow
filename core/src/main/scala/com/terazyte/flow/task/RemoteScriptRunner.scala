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

package com.terazyte.flow.task

import java.io.InputStream
import java.util.Scanner
import java.util.concurrent.ConcurrentLinkedQueue

import net.schmizz.sshj.SSHClient

class RemoteScriptRunner(ssh: SSHClient) {
  type HandleOp = String => Unit
  val logQueue = new ConcurrentLinkedQueue[String]()

  val defaultHandler: HandleOp = (s: String) => {
    logQueue.add(s)
  }

  def execScript(path: String): Either[Throwable, String] = {
    val session = ssh.startSession()
    try {

      val run = session.exec(s"bash ${path}")

      val errorStream = new StreamLog(run.getErrorStream, defaultHandler)
      val infoStream  = new StreamLog(run.getInputStream, defaultHandler)
      errorStream.start()
      infoStream.start()
      errorStream.join(Long.MaxValue)
      infoStream.join(Long.MaxValue)
      val exitCode   = run.getExitStatus
      val msgBuilder = new StringBuilder
      while (!logQueue.isEmpty) {
        msgBuilder.append(logQueue.remove() + "\n")
      }
      if (exitCode != null && exitCode != 0) {

        Left(new Exception(s"Exit code : ${exitCode} \n ${msgBuilder.mkString}"))
      } else {
        Right(msgBuilder.mkString)
      }
    } finally {
      session.close()
      ssh.close()
    }

  }

  class StreamLog(stream: InputStream, opHandler: HandleOp) extends Thread {

    var properties: Map[String, Any] = Map.empty

    override def run(): Unit = {
      val scanner = new Scanner(stream, "UTF-8").useDelimiter("\n")
      while (scanner.hasNext) {
        opHandler(scanner.next())

      }
      scanner.close()
    }

  }

}
