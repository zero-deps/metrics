package .stats

import annotation.unused

import zero.ext.{seq=>_,_}, option._
import zio._, nio._, core._, clock._, stream._
import kvs.{Err=>_,Throwed=>_,_}, seq._
import ftier._, ws._, udp._
import com.typesafe.config.ConfigFactory
import zd.proto._, api._
import java.util.concurrent.TimeUnit.{MILLISECONDS => `in ms`, HOURS => `in hours`}

sealed trait NewEvent
case class Broadcast(msg: Push) extends NewEvent

case class KvsErr(e: kvs.Err) extends ForeignErr

object StatsApp extends zio.App {
  implicit object EnDataCodec extends DataCodec[EnData] {
    import zd.proto._, macrosapi._
    implicit val c = caseCodecAuto[EnData]
    def extract(xs: Bytes): EnData = api.decode[EnData](xs)
    def insert(x: EnData): Bytes = api.encodeToBytes(x)
  }

  type Clients = Set[WsContextData]
  
  val httpHandler: http.Request => ZIO[Kvs, Err, http.Response] = {
    case UpgradeRequest(r) => upgrade(r)
  }

  def msgHandler(@unused queue: Queue[NewEvent]): Pull => ZIO[Kvs with WsContext, Err, Unit] = {
    case ask: HealthAsk => ZIO.unit
  }

  def wsHandler(queue: Queue[NewEvent], clients: Ref[Clients]): Msg => ZIO[WsContext with Kvs, Err, Unit] = {
    case msg: Binary =>
      for {
        message  <- IO.effect(decode[Pull](msg.v.toArray)).mapError(Throwed)
        _        <- msgHandler(queue)(message).catchAllCause(cause =>
                        IO.effect(println(s"msg ${cause.failureOption} err ${cause.prettyPrint}"))
                    ).fork.unit
      } yield ()
    case Open =>
      for {
        ctx   <- ZIO.access[WsContext](_.get)
        _     <- clients.update(_ + ctx)
        nodes <- Kvs.all[EnData](fid(fid.Nodes()))
        _     <- nodes.map(_._2).mapError(KvsErr).foreach(en => send(HostMsg(host=en.host.getOrElse(""), ipaddr=en.value, time=en.time.getOrElse(0))))
      } yield ()
    case Close =>
      for {
        ctx <- ZIO.access[WsContext](_.get)
        _   <- clients.update(_ - ctx)
      } yield ()
    case msg => Ws.close
  }

  def broadcast(clients: Ref[Clients], msg: Push): IO[Err, Unit] = {
    for {
      xs <- clients.get
      _  <- ZStream.fromIterable(xs).foreach(ctx => send(msg).provideLayer(ZLayer.succeed(ctx)))
    } yield ()
  }

  def workerLoop(q: Queue[NewEvent], @unused clients: Ref[Clients]): ZIO[Kvs with Clock, Err, Unit] = q.take.flatMap{
    case Broadcast(x) =>
      broadcast(clients, x)
  }

  val conf = store.RngConf()
  val leveldbConf = conf.conf.leveldbConf

  type Stats = Has[client.Stats]
  def stats(leveldbConf: store.Rng.LvlConf): URLayer[ActorSystem, Stats] = {
    ZLayer.fromService(client.Stats(leveldbConf.dir))
  }

  val app: ZIO[ActorSystem, Any, Unit] = {
    (for {
      q    <- Queue.unbounded[NewEvent]
      clients <- Ref.make(Set.empty: Clients)
      ucfg <- IO.effect(ConfigFactory.load).orDie
      uhos <- IO.effect(ucfg.getString("stats.server.host")).orDie
      upor <- IO.effect(ucfg.getInt   ("stats.server.port")).orDie
      udpa <- SocketAddress.inetSocketAddress(uhos, upor)
      _    <- udp.bind(udpa)(channel =>
                (for {
                  data <- channel.read
                  msg  <- IO.effect(decode[client.ClientMsg](data.toArray))
                  (host, ipaddr) = (msg.host, msg.ipaddr)
                  time <- currentTime(`in ms`)
                  _    <- Kvs.put(fid(fid.Nodes()), en_id.str(host), EnData(value=ipaddr, time=time, host=host))
                  _    <- q offer Broadcast(HostMsg(host=host, ipaddr=ipaddr, time=time))
                  _    <- msg match {
                    case x: client.MetricMsg if x.name == "cpu_mem"  =>
                      for {
                        /* live (cpu+mem) */
                        _   <- Kvs.array.add(fid(fid.CpuMemLive(host)), size=20, EnData(value=x.value, time=time))
                        /* day (cpu) */
                        cpu   = x.value.split('~').head.some.filter(_.nonEmpty).flatMap(_.toIntOption).getOrElse(0)
                        hour <- currentTime(`in hours`).map(_ % 24).map(_ + 1) /* ∊ [1, 24] */
                        old  <- Kvs.array.get(fid(fid.CpuDay(host)), idx=hour).map(_.flatMap(_.value.toIntOption).getOrElse(0))
                        _    <- Kvs.array.put(fid(fid.CpuDay(host)), idx=hour, EnData(value=Math.max(old, cpu).toString))
                      } yield ()
                    case x: client.MetricMsg if x.name == "kvs.size" => ZIO.unit.map(_ => println(x)) //todo: Console.live
                    case x: client.MetricMsg if x.name == "feature"  => ZIO.unit.map(_ => println(x)) //todo: Console.live
                    case x: client.MetricMsg =>
                      for {
                        _ <- Kvs.put(fid(fid.Metrics(host)), en_id(en_id.Metric(x.name)), EnData(value=x.value, time=time))
                      } yield ()
                    case x: client.MeasureMsg => ZIO.unit.map(_ => println(x)) //todo: Console.live
                    case x: client.ErrorMsg   => ZIO.unit.map(_ => println(x)) //todo: Console.live
                    case x: client.ActionMsg  => ZIO.unit.map(_ => println(x)) //todo: Console.live
                  }
                } yield ()).catchAll(ex => ZIO.unit.map(_ => println(ex))) //todo: Logging.live
              ).use(ch => ZIO.never.ensuring(ch.close.ignore)).fork
      _    <- workerLoop(q, clients).forever.fork
      addr <- SocketAddress.inetSocketAddress(8001)
      kvs  <- ZIO.access[Kvs](_.get)
      _    <- httpServer.bind(
                addr,
                IO.succeed(req => httpHandler(req).provideLayer(ZLayer.succeed(kvs))),
                IO.succeed(msg => wsHandler(q, clients)(msg).provideSomeLayer[WsContext](ZLayer.succeed(kvs)))
              )
    } yield ()).provideLayer(Clock.live ++ stats(leveldbConf) ++ Kvs.live(conf) ++ Udp.live(client.conf.msgSize))
  }

  def run(@unused args: List[String]): URIO[ZEnv, ExitCode] = {
    app.provideLayer(actorSystem("Stats")).exitCode
  }
}