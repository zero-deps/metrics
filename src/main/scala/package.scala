package 

import akka.actor.ActorRef
import akka.stream.stage.GraphStageLogic.StageActor
import akka.stream.stage.{GraphStage, GraphStageLogic, OutHandler, StageLogging}
import akka.stream.{Attributes, Outlet, SourceShape}
import scala.collection.immutable.Queue
import scalaz.Scalaz._

import zd.proto.api.{N, MessageCodec, encode, decode}
import zd.proto.macrosapi.{caseCodecAuto, caseCodecNums}


package object stats {
  sealed trait Stat
  final case class MetricStat(name: String, value: String) extends Stat
  final case class MeasureStat(name: String, value: String) extends Stat
  final case class ErrorStat(exception: String, stacktrace: String, toptrace: String) extends Stat
  final case class ActionStat(action: String) extends Stat

  final case class StatMeta(time: String, addr: String)

  final case class Msg(stat: Stat, meta: StatMeta)

  def now_ms(): String = System.currentTimeMillis.toString

  final case class StatEn
    ( @N(1) fid: String
    , @N(2) id: String
    , @N(3) prev: String
    , @N(4) data: String
    , @N(5) time: String
    , @N(6) addr: String) extends kvs.en.En

  implicit object FdHandler extends kvs.en.FdHandler {

    implicit val fdCodec: MessageCodec[kvs.en.Fd] = caseCodecNums[kvs.en.Fd]('id->1, 'top->2, 'count->3)

    def pickle(e: kvs.en.Fd): kvs.Res[Array[Byte]] = encode[kvs.en.Fd](e).right
    def unpickle(a: Array[Byte]): kvs.Res[kvs.en.Fd] = decode[kvs.en.Fd](a).right
  }

  implicit object StatEnHandler extends kvs.en.EnHandler[StatEn] {
    val fh: kvs.en.FdHandler = FdHandler

    implicit val statEnCodec: MessageCodec[StatEn] = caseCodecAuto[StatEn]

    def pickle(e: StatEn): kvs.Res[Array[Byte]] = encode(e).right
    def unpickle(a: Array[Byte]): kvs.Res[StatEn] = decode[StatEn](a).right

    protected def update(en: StatEn, prev: String): StatEn = en.copy(prev=prev)
    protected def update(en: StatEn, id: String, prev: String): StatEn = en.copy(id=id, prev=prev)
  }

  class MsgSource(sourceFeeder: ActorRef) extends GraphStage[SourceShape[Msg]] {
    val out: Outlet[Msg] = Outlet("MessageSource")
    override val shape: SourceShape[Msg] = SourceShape(out)
    override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = {
      new GraphStageLogic(shape) with StageLogging {
        var messages: Queue[Msg] = Queue()
        lazy val self: StageActor = getStageActor({
          case (_, msg: Msg) =>
            log.debug("received msg, queueing: {} ", msg)
            messages = messages.enqueue(msg)
            pump()
          case _ =>
        })
        setHandler(out, new OutHandler {
          override def onPull(): Unit = {
            log.debug("onPull() called...")
            pump()
          }
        })
        private def pump(): Unit = {
          if (isAvailable(out) && messages.nonEmpty) {
            log.debug("ready to dequeue")
            messages.dequeue match {
              case (msg: Msg, newQueue: Queue[Msg]) =>
                log.debug("got message from queue, pushing: {} ", msg)
                push(out, msg)
                messages = newQueue
            }
          }
        }
        override def preStart(): Unit = {
          log.debug("pre-starting stage, assigning StageActor to sourceFeeder")
          sourceFeeder ! self.ref
        }
        private def onMessage(x: (ActorRef, Any)): Unit = {
          x match {
            case (_, msg: Msg) =>
              log.debug("received msg, queueing: {} ", msg)
              messages = messages.enqueue(msg)
              pump()
            case _ =>
          }
        }
      }
    }
  }
}
