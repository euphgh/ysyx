package macros

import scala.annotation.{compileTimeOnly, StaticAnnotation}
import scala.language.experimental.macros
import scala.reflect.macros.whitebox

import chisel3._
import chisel3.util._
import chisel3.util.experimental.decode._
import scala.collection._

package object decode {

  type InstrPat   = Seq[(BitPat, List[ChiselEnum#Type])]
  type DBundleMap = SeqMap[String, (ChiselEnum, BitPat)]

  @compileTimeOnly("enable macro paradise to expand macro annotations")
  class DecodeMacro extends StaticAnnotation {
    def macroTransform(annottees: Any*): Any = macro DecodeMacro.impl
  }

  object DecodeMacro {
    def impl(c: whitebox.Context)(annottees: c.Expr[Any]*): c.Expr[Any] = {
      import c.universe._
      annottees.foreach(x => println(x.tree.getClass()))
      def bodyChange(body: List[Tree]): List[Tree] = {
        val pairsString = body.map {
          case q"val $name = $obj.$value" =>
            s""""$name" -> ($obj, BitPat($obj.$value.litValue.U($obj.getWidth.W)))"""
          case q"val $name = $obj()" =>
            s""""$name" -> ($obj, BitPat("b" + "?" * $obj.getWidth))"""
          case _ => c.abort(c.enclosingPosition, "decode bundle only Enum value")
        }.mkString("override val defaultMap = SeqMap(", ", ", ")")

        body.map {
          case q"val $name = $obj.$_" =>
            q"val $name = $obj()"
          case q"val $name = $obj()" =>
            q"val $name = $obj()"
          case _ => c.abort(c.enclosingPosition, "decode bundle only Enum value")
        } ++ Seq(
          q"import chisel3.util.BitPat",
          q"import scala.collection.SeqMap",
          c.parse(pairsString),
          q"println(defaultMap)"
        )
      }

      annottees.map(_.tree) match {
        case (param: ClassDef) :: Nil => {
          param match {
            case q"class $cName(...$fields) extends ..$base { ..$body }" => {
              val newBody  = bodyChange(body)
              val newClass = q"class $cName(...$fields) extends ..$base { ..$newBody }"
              println(s"new Class is $newClass")
              c.Expr(newClass)
            }
            case q"class $cName extends ..$base { ..$body }" => {
              val newBody  = bodyChange(body)
              val newClass = q"class $cName extends ..$base { ..$newBody }"
              println(s"new Class is $newClass")
              c.Expr(newClass)
            }
            case _ => {
              c.abort(c.enclosingPosition, "no match class")
            }
          }
        }

        case _ =>
          c.abort(c.enclosingPosition, "not class def")
      }
    }
  }

  abstract class DecodeUtils {

    val allInstr: InstrPat = Seq()

    def decode(
      input:     UInt,
      outputs:   DCBundle,
      minimizer: Minimizer = QMCMinimizer
    ) = {
      outputs := decoder(minimizer, input, mergeTable(outputs.defaultMap)).asTypeOf(outputs)
    }

    private def mergeTable(defaultValue: DBundleMap) = {
      val enumTable = allInstr.map {
        case (instrPat, instrEnum) =>
          val bundleEnums = defaultValue.map {
            case (_, (enumObj, defaultPat)) =>
              val option = instrEnum.find {
                case _: enumObj.Type => true
                case _ => false
              }
              option.fold(defaultPat)(x => BitPat(x.litValue.U(enumObj.getWidth.W)))
          }

          (instrPat, bundleEnums.tail.foldLeft(bundleEnums.head)(_ ## _))
      }

      val bundleWidth = defaultValue.map {
        case (_, (enumObj, _)) => enumObj.getWidth
      }.sum

      TruthTable(enumTable, BitPat("b" + "?" * bundleWidth))
    }
  }

  trait DCBundle extends Bundle {
    val defaultMap: DBundleMap = SeqMap()
  }

}
