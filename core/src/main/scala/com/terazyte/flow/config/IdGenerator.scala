package com.terazyte.flow.config
import java.util.Date

trait IdGenerator {

  def generate(prefix: String = ""): String =
    if (prefix.isEmpty) new Date().getTime().toString else prefix + "-" + new Date().getTime

}
