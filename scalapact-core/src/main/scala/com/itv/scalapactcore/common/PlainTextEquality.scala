package com.itv.scalapactcore.common

object PlainTextEquality {

  def check(expected: String, received: String): Boolean = {
    expected == received.trim
  }

}
