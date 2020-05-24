package scala.scalajs.js

trait |[T, U]

trait T1
object T1 {
  val someT1: T1 = null
}

trait T2
object T2 {
  val someT2: T2 = null
}
trait T3

object SmartUnion {
  type T = T1 | T2 | T3
  val someT3: T3 = null
  def test(t: T) = ???
  test(/*caret*/)
}
/*
someT1
someT2
someT3
*/
