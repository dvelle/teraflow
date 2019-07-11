package com.terazyte.flow.stages

//import akka.actor.ActorContext
//import com.terazyte.launcher.job.{Build, Task}
//import com.terazyte.launcher.parser.LaunchJobConfig
//import com.terazyte.launcher.task.{CloneProject, CmdDef, ExecCmd, SourceProject}

import scala.collection.immutable.Queue

//object BuildStage extends StageBuilder {
//
//  override def build(context: ActorContext, config: LaunchJobConfig): Queue[Task] = {
//
//    val sourceProject = SourceProject(config.project, config.build.workDir, Some(config.build.source))
//    val buildCmdDef   = CmdDef(config.build.cmd, config.build.workDir)
//    val cloneTask     = CloneProject.build(Build, context, sourceProject)
//    val buildCmdTask  = ExecCmd.build(Build, context, buildCmdDef)
//
//    Queue(cloneTask, buildCmdTask)
//  }
//
//}
