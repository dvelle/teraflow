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

package com.terazyte.flow.config.remote

import java.io.{File, IOException}

import com.terazyte.flow.config.{DataTransferable, ExecTarget, Resource, ResourceConfig}
import com.terazyte.flow.steps.BashScript
import com.terazyte.flow.task.RemoteScriptRunner
import com.terazyte.flow.task.common.FileHelper
import net.jcazevedo.moultingyaml.YamlValue
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.transport.verification.PromiscuousVerifier
import net.schmizz.sshj.userauth.keyprovider.PKCS8KeyFile

import scala.util.Try

case class RemoteHost(alias: String, host: String, username: String, password: Option[String], keyFile: Option[String])
    extends ExecTarget
    with ResourceConfig
    with DataTransferable {

  def exec(script: BashScript): Either[Throwable, String] = {
    (script.path, script.value) match {
      case (Some(scriptPath), None) =>
        val runner = new RemoteScriptRunner(getSSHClient())
        runner.execScript(scriptPath)

      case (None, Some(scriptVal)) =>
        val ssh     = getSSHClient()
        val session = ssh.startSession()

        val remoteScriptDir = s"/home/${username}/.launcher/scripts"

        val run = session.exec(s"mkdir -p ${remoteScriptDir}")

        run.join()
        session.close()
        val dirCreated = run.getExitStatus
        if (dirCreated == 0) {
          val localPath        = FileHelper.writeTo(scriptVal)
          val fileName         = new File(localPath).getName()
          val remoteScriptPath = s"${remoteScriptDir}/${fileName}"
          ssh.newSCPFileTransfer().upload(localPath, s"${remoteScriptDir}/")

          ssh.startSession().exec(s"chmod +x ${remoteScriptPath}").join()
          new RemoteScriptRunner(ssh).execScript(remoteScriptPath)
        } else {
          Left(new IOException(s"Unable to create directory for storing scripts in ${host}"))
        }

      case _ => Left(new IllegalArgumentException("Either path or value should be specified for running the script"))
    }

  }

  def execCmd(cmd: String): Either[Throwable, Unit] = {

    lazy val cmdRes = getSSHClient().startSession().exec(cmd)
    Try(cmdRes.join()).toEither
  }

  override def name(): String = host

  def getSSHClient(): SSHClient = {
    val ssh = new SSHClient()
    ssh.loadKnownHosts()

    (password, keyFile) match {
      case (Some(pass), Some(key)) => usingKey(ssh, key)
      case (Some(pass), None)      => usingPassword(ssh, pass)
      case (None, Some(key))       => usingKey(ssh, key)
    }
  }

  override def execCopy(from: String, to: String): Unit = {
    val ssh = getSSHClient()
    ssh.addHostKeyVerifier(new PromiscuousVerifier)
    ssh.newSCPFileTransfer().upload(from, to)
  }

  private def usingPassword(ssh: SSHClient, pass: String): SSHClient = {

    ssh.setConnectTimeout(5000)
    ssh.authPassword(username, pass)
    ssh.connect(host)
    ssh
  }

  private def usingKey(ssh: SSHClient, keyFilePath: String): SSHClient = {

    val keyFile       = new PKCS8KeyFile()
    val knownHostFile = System.getProperty("user.home") + "/.ssh/known_hosts"
    ssh.addHostKeyVerifier(new ConsoleSSHVerifier(new File(knownHostFile), System.console()))
    keyFile.init(new File(keyFilePath))
    ssh.setConnectTimeout(5000)
    ssh.connect(host)
    ssh.authPublickey(username, keyFile)
    ssh
  }

}

import net.jcazevedo.moultingyaml.DefaultYamlProtocol._

object RemoteHostYamlParser extends Resource[RemoteHost] {

  override val id: String       = "server"
  implicit val remoteHostFormat = yamlFormat5(RemoteHost.apply)
  override def parse(v: YamlValue): RemoteHost = {
    v.convertTo[RemoteHost]
  }
}
