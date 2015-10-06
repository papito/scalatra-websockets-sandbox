package com.whyisitdoingthat.controllers

import org.json4s.{JsonDSL, JValue, DefaultFormats, Formats}
import org.scalatra.SessionSupport
import org.scalatra._
import org.scalatra.json.{JacksonJsonSupport, JValueResult}
import org.slf4j.LoggerFactory
import org.scalatra.atmosphere._
import JsonDSL._

import scala.concurrent.ExecutionContext.Implicits.global

class WebsocketController extends ScalatraServlet with JValueResult with JacksonJsonSupport with SessionSupport with AtmosphereSupport   {
  private final val log = LoggerFactory.getLogger(getClass)

  implicit protected val jsonFormats: Formats = DefaultFormats

  atmosphere("/") {
    new AtmosphereClient {

      private def write(jsonMessage: JValue): Unit = {
        log.info(s"WS -> $jsonMessage")
        this.send(jsonMessage)
      }

      override def receive: AtmoReceive = {
        case TextMessage("uuid") => {
          log.info(s"WS <- uuid")
          this.write(compact(render("uuid" -> uuid)))
        }

        case JsonMessage(json: JValue) => {
          log.info(s"WS <- $json")
          this.write(json)
        }

        case Connected =>
          log.info("Client connected")

        case Disconnected(disconnector, Some(error)) =>
          log.info("Client disconnected ")

        case Error(Some(error)) =>
          // FIXME - what is the difference with the hanler "error" handler?
          error.printStackTrace()
      }
    }
  }
}
