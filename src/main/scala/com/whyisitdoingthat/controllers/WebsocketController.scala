package com.whyisitdoingthat.controllers

import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

import org.json4s.JsonAST.{JField, JObject, JString}
import org.json4s.{DefaultFormats, Formats, JValue, JsonDSL}
import org.scalatra.SessionSupport
import org.scalatra._
import org.scalatra.json.{JValueResult, JacksonJsonSupport}
import org.scalatra.atmosphere._
import JsonDSL._
import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.routing.RoundRobinPool

import scala.concurrent.ExecutionContext

class WebsocketController
  extends ScalatraServlet
    with JValueResult
    with JacksonJsonSupport
    with SessionSupport
    with AtmosphereSupport   {

  implicit protected val jsonFormats: Formats = DefaultFormats

  private val numWorkers = sys.runtime.availableProcessors()
  private val workerPool = Executors.newFixedThreadPool(numWorkers)
  implicit val ec: ExecutionContext = ExecutionContext.fromExecutorService(workerPool)

  case object ActorMessage

  atmosphere("/") {
    new AtmosphereClient {
      private def uuidJson: JObject = "uid" -> uuid

      class WorkerActor(client: AtmosphereClient) extends Actor {
        override def receive: Actor.Receive = {
          case ActorMessage => {
            println(s"Starting work on actor ${self.path}")
            Thread.sleep(10000)
            println(s"Finished work on actor ${self.path}")

            val resp: JValue = "status" -> "success"
            client.send(resp)
            println(s"Sending message to self ${self.path}")

            if (!stopWorkers.get) {
              self ! ActorMessage
            }
          }
        }

        override def preStart(): Unit = {
          println(s"Actor ${self.path} starting")
        }

        override def postStop(): Unit = {
          println(s"Actor ${self.path} stopped")
        }
      }

      private val actorSys = ActorSystem()
      private val akkaRouter: ActorRef = actorSys.actorOf(
        RoundRobinPool(numWorkers).props(Props(new WorkerActor(this))), "router")

      private var stopWorkers: AtomicBoolean = new AtomicBoolean(false)

      private def writeToYou(jsonMessage: JValue): Unit = {
        println(s"YOU -> $jsonMessage")
        this.send(jsonMessage)
      }

      private def writeToAll(jsonMessage: JValue): Unit = {
        println(s"ALL -> $jsonMessage")
        this.broadcast(jsonMessage, Everyone)
      }

      private def writeToRest(jsonMessage: JValue): Unit = {
        println(s"REST -> $jsonMessage")
        this.broadcast(jsonMessage)
      }

      override def receive: AtmoReceive = {
        case message @ JsonMessage(JObject(JField("action", JString("getUID")) :: _)) => {
          val json: JValue = message.content
          println(s"WS <- $json")
          this.writeToYou(uuidJson)
        }

        // add "trello" card
        case message @ JsonMessage(JObject(JField("action", JString("addCard")) :: _)) => {
          val json: JValue = message.content
          println(s"WS <- $json")

          val cardJson: JValue = json findField {
            case JField("card", _) => true
            case _ => false
          }

          this.writeToAll(cardJson)
        }

        // workers
        case message @ JsonMessage(JObject(JField("action", JString("startWorkers")) :: _)) => {
          val json: JValue = message.content
          println(s"WS <- $json")

          startWorkersParty()

          this.writeToYou("workersStarted" -> true)
        }

        case message @ JsonMessage(JObject(JField("action", JString("stopWorkers")) :: _)) => {
          val json: JValue = message.content
          println(s"WS <- $json")
          stopWorkers.set(true)
          this.writeToYou("workersStarted" -> false)
        }

        // unknown
        case message @ JsonMessage(AnyRef) => {
          val json: JValue = message.content
          println(s"WS <- $json")
          throw new Exception("Unknown JSON command")
        }

        case Connected =>
          println("Client connected")

        case Disconnected(_, Some(_)) => {
          actorSys.terminate()
          println("Client disconnected ")
        }

        case Error(Some(error)) =>
          error.printStackTrace()
      }

      private def startWorkersParty(): Unit = {
        stopWorkers.set(false)

        for (_ <- 1 to numWorkers) {
          println(s"Sending message to router")
          akkaRouter ! ActorMessage
          println(s"Sent message to to router")
        }
      }
    }
  }
}
