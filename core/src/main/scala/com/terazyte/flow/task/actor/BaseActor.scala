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

package com.terazyte.flow.task.actor

import akka.actor.{Actor, ActorLogging, Terminated}
import akka.util.Timeout

import scala.concurrent.duration.Duration
import scala.concurrent.duration._
abstract class BaseActor extends Actor with ActorLogging {

  implicit def system     = context.system
  def scheduler           = system.scheduler
  implicit def dispatcher = system.dispatcher
  implicit val timeout = Timeout(1.seconds)
  def stopChildren(): Unit = {
    context.children foreach { child =>
      context.unwatch(child)
      context.stop(child)
    }
  }

  override def preStart(): Unit = context.setReceiveTimeout(Duration.Undefined)

  override def preRestart(reason: Throwable, message: Option[Any]): Unit =
    log.error(s"Actor $this crashed on message $message", reason)

  override def unhandled(message: Any): Unit =
    message match {
      case Terminated(dead) => log.error(s"Unhandled message : ${message}")
      case unknown =>
        throw new IllegalArgumentException(s"Actor $this doesn't support message $unknown")
    }

}
