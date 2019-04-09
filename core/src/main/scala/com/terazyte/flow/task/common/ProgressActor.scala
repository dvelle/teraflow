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

import akka.actor.{Cancellable, Props}
import com.terazyte.flow.task.actor.BaseActor
import com.terazyte.flow.task.common.ProgressActor._
import org.fusesource.jansi.Ansi._
import org.fusesource.jansi.Ansi.Color._

import scala.concurrent.duration._

class ProgressActor extends BaseActor {

  private val frames = Array("⠋", "⠙", "⠹", "⠸", "⠼", "⠴", "⠦", "⠧", "⠇", "⠏")
  var frameIndex     = 0
  val frameLen       = frames.length

  def inProgress(text: String, frameIndex: Int, isFirst: Boolean, progressTask: Option[Cancellable]): Receive = {

    case NextFrame =>
      val printVal = frames(frameIndex) + " " + text

      print(
        ansi()
          .eraseLine()
          .cursorToColumn(0)
          .fg(CYAN)
          .a(printVal)
          .cursorToColumn(printVal.length + 2)
      )
      val nextIndex = if (frameIndex == frameLen - 1) 0 else frameIndex + 1
      context.become(inProgress(text, nextIndex, false, progressTask))

    case ShowSuccess(message, onComplete) =>
      progressTask.map(_.cancel())
      println(
        ansi()
          .cursorToColumn(0)
          .eraseLine()
          .fg(GREEN)
          .a(s"\u2713 ${message}")
          .reset()
      )
      if (onComplete.nonEmpty) {
        println(ansi().a(onComplete))
      }
      context.become(stopped)

    case ShowSkipped(message) =>
      progressTask.map(_.cancel())
      println(
        ansi()
          .cursorToColumn(0)
          .eraseLine()
          .fg(YELLOW)
          .a(s"\u23E9 ${message}")
          .reset())
      context.become(stopped)

    case ShowFailure(message, cause) =>
      progressTask.map(_.cancel())
      println(
        ansi()
          .cursorToColumn(0)
          .eraseLine()
          .fg(RED)
          .a(s"\u2A2F ${message}")
          .reset())

      println(ansi().a(cause))
      context.become(stopped)

  }

  def stopped: Receive = {

    case ShowProgress(message,tailLogs) =>
      val cancellable =if(!tailLogs){
        Some(scheduler.schedule(0 milliseconds, 100 milliseconds, self, NextFrame))
      } else {
        println(
          ansi()
            .eraseLine()
            .cursorToColumn(0)
            .fg(CYAN)
            .a(s"Running : ${message}").reset())
        None
      }

      context.become(inProgress(message, 0, true, cancellable))

    case NextFrame =>
      //Do nothing
      println

  }

  override def receive: Receive = stopped

}

object ProgressActor {

  def props(): Props = Props(new ProgressActor)

  case object NextFrame
  case class ShowProgress(message: String, tailLogs: Boolean)
  case class ShowSuccess(message: String, onComplete: String = "")
  case class ShowSkipped(message: String)
  case class ShowFailure(message: String, cause: String)
}
