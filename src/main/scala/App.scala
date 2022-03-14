import cats.effect.concurrent.Ref
import cats.effect.{Blocker, Resource, ExitCode, IOApp, IO}
import cats.syntax.all._
import sttp.client3._
import sttp.client3.asynchttpclient.fs2.AsyncHttpClientFs2Backend

import java.time.{Duration => JavaDuration}
import scala.concurrent.duration._
import tg.api._
import functions._

object App extends IOApp {
  val ornulRate = 100
  val ornulDelay: JavaDuration = JavaDuration.ofMinutes(30)
  val ignoreRewindFlag: Boolean = sys.env.getOrElse("BOT_NO_REWIND", "TRUE").toBoolean
  val deployDate: Long = System.currentTimeMillis() / 1000L

  def mkFn(implicit b: SttpBackend[IO, Any]): Resource[IO, BotFunction] =
    (sedFunction.resource, tyanochkuFunction.resource, ornulFunction.resource(ornulRate, ornulDelay))
      .mapN { (sed, tyan, ornul) => sed ++ tyan ++ ornul }

  def filterObsoleteMessages(x: models.Update): Boolean = ignoreRewindFlag match {
    case true => (for {
      message <- x.message.orElse(x.edited_message)
      isObsolete = message.date < deployDate
    } yield isObsolete).getOrElse(false)
    case _ => false
  }

  def loop(offsetRef: Ref[IO, Long], fn: BotFunction)(implicit b: SttpBackend[IO, Any]): IO[Unit] =
    for {
      offset <- offsetRef.get
      updates <- getUpdates(offset)
      validUpdates = updates.filter(filterObsoleteMessages)
      _ <- validUpdates.traverse_(fn.handleUpdate)
      _ <- validUpdates.maxByOption(_.update_id).map(_.update_id + 1).traverse(offsetRef.set)
      _ <- IO.sleep(200.millis)
    } yield ()

  override def run(args: List[String]): IO[ExitCode] = {
    val res = for {
      blocker <- Blocker[IO]
      implicit0(b: SttpBackend[IO, Any]) <- AsyncHttpClientFs2Backend.resource[IO](blocker)
      fn <- mkFn
      _ <- Resource.eval {
        Ref.of[IO, Long](0).flatMap(offsetRef => loop(offsetRef, fn).foreverM)
      }
    } yield ExitCode.Success

    res.use(IO.pure)
  }
}
