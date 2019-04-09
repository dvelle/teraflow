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

package com.terazyte.flow.parser

import com.terazyte.flow.config.ResourceConfig
import com.terazyte.flow.config.remote.RemoteHost

import scala.util.parsing.combinator.RegexParsers

trait RemoteTargetConfig
case class HostMeta(user: String, host: String, targetPath: String) extends RemoteTargetConfig
case class RemoteAlias(alias: String, targetPath: String)           extends RemoteTargetConfig

trait RemoteHostRegexParser extends RegexParsers {
  def user: Parser[String]     = """[a-zA-Z0-9\\-]+""".r ^^ { _.toString }
  def atSymbol: Parser[String] = """@""".r ^^ { _.toString }
  def colon: Parser[String]    = """:""".r ^^ { _.toString }

  def hostName: Parser[String] =
    """([a-zA-Z0-9\\-]+[.]?)+""".r ^^ {
      _.toString
    }
  def target: Parser[String] = """^(/[^/ ]*)+/?$""".r ^^ { _.toString }
  def hostMeta: Parser[HostMeta] = user ~ atSymbol ~ hostName ~ colon ~ target ^^ {
    case u ~ at ~ h ~ c ~ t => HostMeta(u, h, t)
  }

  def aliasHost: Parser[RemoteAlias] = hostName ~ colon ~ target ^^ {
    case h ~ c ~ t => RemoteAlias(h, t)
  }
}

object RemoteHostParser extends RemoteHostRegexParser {

  def parseRemoteHost(host: String, secrets: Seq[ResourceConfig]): Either[Throwable, (RemoteHost, String)] = {
    val hostInfo = parse(hostMeta, host)
    val mayBeHost = hostInfo.map { hmeta =>
      val searchByHost = secrets.find(x =>
        x match {
          case x: RemoteHost => x.host == hmeta.host
          case _             => false
      })
      searchByHost
        .map(x => (x.asInstanceOf[RemoteHost], hmeta.targetPath))
    }

    mayBeHost match {
      case Success(v, _) =>
        v match {
          case Some(rs) => Right(rs)
          case None     => Left(new RuntimeException(s"Configuration not found for host: ${host}"))
        }
      case Failure(_, _) =>
        val parsedAlias = parse(aliasHost, host).map { ra =>
          secrets
            .find(x =>
              x match {
                case sc: RemoteHost => sc.alias.equalsIgnoreCase(ra.alias)
                case _              => false
            })
            .map(x => (x.asInstanceOf[RemoteHost], ra.targetPath))
            .toRight(new RuntimeException(s"Configuration for alias, ${ra.alias} not found"))
        }

        parsedAlias match {
          case Success(v, _)  => v
          case Failure(ex, _) => Left(new RuntimeException(ex))
        }
    }

  }

}
