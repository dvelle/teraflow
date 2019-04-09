package com.terazyte.flow.emr

import org.scalatest.{FlatSpec, Matchers}

class EMRClusterSpec extends FlatSpec with Matchers {

  behavior of "EMRConfig parser"

  it should "parse the config" in {

    val configFile = getClass.getResource("/emr.yaml").getFile
    val obj        = EMRClusterConfigParser.fromFile(configFile)

    obj.isRight shouldBe (true)

    obj.right.get.name shouldBe ("project-name")

  }
}
