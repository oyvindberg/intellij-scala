package scala.scalajs.js

trait |[T, U]

trait T1[X]
object T1 {
  def someT1[XX]: T1[XX] = null
}

trait T2[Y]
object T2 {
  def no: T2[Int] = null // doesn't conform to AnyRef below
  def someT2[YY]: T2[YY] = null
}

object SmartUnion {
  type T[XXX, YYY] = T1[XXX] | T2[YYY]
  def test[Z <: AnyRef](t: T[Z, Z]) = ???
  test(/*caret*/)
}
/*
someT1
someT2
*/
