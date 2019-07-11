/*
 * Copyright 2019 Gigahex
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

package com.terazyte.flow.emr

import awscala._
import awscala.emr._
import com.amazonaws.auth.profile.ProfileCredentialsProvider
import com.amazonaws.services.elasticmapreduce.model.{AddJobFlowStepsRequest, Application, BootstrapActionConfig, Configuration, DescribeClusterRequest, EbsConfiguration, HadoopJarStepConfig, InstanceGroupConfig, JobFlowInstancesConfig, ListInstancesRequest, RunJobFlowRequest, ScriptBootstrapActionConfig, StepConfig, Tag, VolumeSpecification}
import com.amazonaws.services.elasticmapreduce.{AmazonElasticMapReduce, AmazonElasticMapReduceClientBuilder, model}
import com.terazyte.flow.config.remote.RemoteHost
import com.terazyte.flow.config.{DataTransferable, Resource, ResourceConfig}
import com.terazyte.flow.steps.BashScript
import com.terazyte.flow.task.common.FileHelper
import net.jcazevedo.moultingyaml.YamlValue

import scala.collection.JavaConverters._

case class EMRTarget(alias: String,
                     awsProfile: Option[String],
                     clusterId: String,
                     region: Option[String],
                     keyFile: Option[String])
    extends ResourceConfig
    with DataTransferable {

  def emrClient(): AmazonElasticMapReduce =
    AmazonElasticMapReduceClientBuilder
      .standard()
      .withCredentials(new ProfileCredentialsProvider(awsProfile.getOrElse("default")))
      .withRegion(region.getOrElse("us-west-2"))
      .build()

  def exec(script: BashScript): Either[Throwable, String] = {
    lazy val emr  = emrClient()
    val scriptDir = "/home/hadoop/.teraflow/scripts"
    val remoteScriptPath = (script.value, script.path) match {
      case (Some(scriptVal), None) =>
        getClusterMasterIp(emr).map { ip =>
          val masterHost = RemoteHost("", ip, "hadoop", None, keyFile)
          val source     = FileHelper.writeTo(scriptVal)

          val targetPath = s"${scriptDir}/${new File(source).getName}"
          masterHost.execCmd(s"mkdir -p ${scriptDir}")
          masterHost.execCopy(source, targetPath)
          targetPath
        }
      case (None, Some(scriptPath)) => Some(scriptPath)
      case _                        => None
    }

    remoteScriptPath match {
      case Some(scPath) =>
        val req = new AddJobFlowStepsRequest
        req.withJobFlowId(clusterId)
        val sparkStepConf = new HadoopJarStepConfig()
          .withJar("command-runner.jar")
          .withArgs("bash", scPath)
        val scriptStep =
          new StepConfig()
            .withName("Execute Script")
            .withActionOnFailure("CONTINUE")
            .withHadoopJarStep(sparkStepConf)

        req.withSteps(scriptStep)
        val result = emr.addJobFlowSteps(req)
        Right("Step Id: " + result.getStepIds().asScala.toList.mkString(","))

      case None =>
        Left(new IllegalArgumentException(s"Unable to run script in the emr cluster : ${clusterId}"))

    }
  }

  private def getClusterMasterIp(emr: AmazonElasticMapReduce): Option[String] = {
    val masterDNS =
      emr.describeCluster(new DescribeClusterRequest().withClusterId(clusterId)).getCluster.getMasterPublicDnsName

    emrClient
      .listInstances(new ListInstancesRequest().withClusterId(clusterId))
      .getInstances()
      .asScala
      .find(_.getPrivateDnsName.equals(masterDNS)) map (_.getPrivateIpAddress)
  }

  def createCluster(config: EMRClusterConfig): Unit = {
    val emrClient = AmazonElasticMapReduceClientBuilder
      .standard()
      .withCredentials(new ProfileCredentialsProvider(awsProfile.getOrElse("default")))
      .withRegion(region.getOrElse("us-west-2"))
      .build()

    val apps = config.applications.map(a => new Application().withName(a.name))
    val tags = config.tags.map(t => new Tag(t.key, t.value))

    val emrServerConfigs = config.configurations.map(c =>
      new Configuration().withClassification(c.classification).withProperties(c.properties.asJava))

    val bootstrapActConfigs = config.bootstrapActions.map(
      b =>
        new BootstrapActionConfig()
          .withName(b.name)
          .withScriptBootstrapAction(
            new ScriptBootstrapActionConfig(b.scriptBootstrapAction.path, b.scriptBootstrapAction.args.asJava)))

    val visible = config.visibleToAllUsers.getOrElse(true)
    val jobFlowRequest = new RunJobFlowRequest()
      .withName(config.name)
      .withLogUri(config.logUri)
      .withReleaseLabel(config.releaseLabel)
      .withInstances(buildInstancesConfig(config.instances))
      .withApplications(apps: _*)
      .withVisibleToAllUsers(visible)
      .withJobFlowRole(config.jobFlowRole)
      .withServiceRole(config.serviceRole)
      .withTags(tags: _*)
      .withConfigurations(emrServerConfigs: _*)
      .withBootstrapActions(bootstrapActConfigs: _*)

    val result              = emrClient.runJobFlow(jobFlowRequest)
    def newlyCreatedCluster = emrClient.describeCluster(new DescribeClusterRequest().withClusterId(result.getJobFlowId))
    var status              = newlyCreatedCluster.getCluster().getStatus().getState()
    while (!status.toLowerCase.equalsIgnoreCase("waiting")) {
      Thread.sleep(2000)
      status = newlyCreatedCluster.getCluster().getStatus().getState()
      if (status.toLowerCase.equalsIgnoreCase("terminating") || status.toLowerCase.equalsIgnoreCase("terminated")) {
        throw new IllegalStateException(
          s"The cluster failed to start. Check the logs for clusterId, ${result.getJobFlowId}")
      }
    }

  }

  override def execCopy(from: String, to: String): Unit = {
    val emrClient = AmazonElasticMapReduceClientBuilder
      .standard()
      .withCredentials(new ProfileCredentialsProvider(awsProfile.getOrElse("default")))
      .withRegion(region.getOrElse("us-west-2"))
      .build()

    val masterIp = emrClient
      .describeCluster(new DescribeClusterRequest().withClusterId(clusterId))
      .getCluster()
      .getMasterPublicDnsName()

    val masterInstance =
      emrClient
        .listInstances(new ListInstancesRequest().withClusterId(clusterId))
        .getInstances()
        .asScala
        .find(_.getPrivateDnsName.equals(masterIp)) map (_.getPrivateIpAddress)

    val masterHost = RemoteHost(masterIp, masterInstance.get, "hadoop", None, keyFile)
    masterHost.execCopy(from, to)

    implicit val emr =
      EMR(credentialsProvider = new ProfileCredentialsProvider(awsProfile.getOrElse("default"))).at(Region.US_WEST_2)
    val conf = emr.getClusterDetail[Configuration](clusterId, (c: model.Cluster) => {
      c.getConfigurations.get(0)
    })

    println(conf.getConfigurations)

  }

  private def buildInstancesConfig(instance: EMRInstance): JobFlowInstancesConfig = {
    val keepAlive            = instance.keepJobFlowAliveWhenNoSteps.getOrElse(true)
    val terminationProtected = instance.terminationProtected.getOrElse(false)
    var instanceConfig = new JobFlowInstancesConfig()
      .withKeepJobFlowAliveWhenNoSteps(keepAlive)
      .withTerminationProtected(terminationProtected)
      .withEc2SubnetId(instance.ec2SubnetId)

    instanceConfig = instance.ec2KeyName match {
      case Some(keyName) => instanceConfig.withEc2KeyName(keyName)
      case None          => instanceConfig
    }

    instanceConfig = instance.emrManagedMasterSecurityGroup match {
      case Some(sg) => instanceConfig.withEmrManagedMasterSecurityGroup(sg)
      case _        => instanceConfig
    }

    instanceConfig = instance.emrManagedSlaveSecurityGroup match {
      case Some(sg) => instanceConfig.withEmrManagedSlaveSecurityGroup(sg)
      case _        => instanceConfig
    }

    instanceConfig = instance.serviceAccessSecurityGroup match {
      case Some(sasg) => instanceConfig.withServiceAccessSecurityGroup(sasg)
      case _          => instanceConfig
    }

    instanceConfig = instance.additionalMasterSecurityGroups match {
      case Some(xs) => instanceConfig.withAdditionalMasterSecurityGroups(xs: _*)
      case _        => instanceConfig
    }

    instanceConfig = instance.additionalSlaveSecurityGroups match {
      case Some(xs) => instanceConfig.withAdditionalMasterSecurityGroups(xs: _*)
      case _        => instanceConfig
    }
    val instanceGrpConfigs = instance.instanceGroups.map { gp =>
      new model.EbsBlockDeviceConfig
      val ebsBDCs = gp.ebsConfiguration.ebsBlockDeviceConfigs.map { ebsConfig =>
        new model.EbsBlockDeviceConfig().withVolumeSpecification(
          new VolumeSpecification()
            .withSizeInGB(ebsConfig.volumeSpecification.sizeInGB)
            .withVolumeType(ebsConfig.volumeSpecification.volumeType))
      }
      val ebsConfiguration = new EbsConfiguration().withEbsBlockDeviceConfigs(ebsBDCs: _*)
      new InstanceGroupConfig()
        .withInstanceCount(gp.instanceCount)
        .withName(gp.name)
        .withInstanceRole(gp.instanceRole)
        .withMarket(gp.market)
        .withInstanceType(gp.instanceType)
        .withEbsConfiguration(ebsConfiguration)
    }

    instanceConfig.withInstanceGroups(instanceGrpConfigs: _*)

  }
}

import net.jcazevedo.moultingyaml.DefaultYamlProtocol._

object EMRTargetYamlParser extends Resource[EMRTarget] {

  override val id: String       = "emr"
  implicit val remoteHostFormat = yamlFormat5(EMRTarget.apply)
  override def parse(v: YamlValue): EMRTarget = {
    v.convertTo[EMRTarget]
  }
}
