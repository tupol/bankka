package org.tupol.bankka.server

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import org.scalatest.Inside
import org.scalatest.wordspec.AnyWordSpecLike
import org.slf4j.LoggerFactory
import org.tupol.bankka.data.dao.{BankDao, InMemoryAccountDao, InMemoryClientDao, InMemoryTransactionDao}
import org.tupol.bankka.data.model.{AccountId, Client, ClientId, CompletedTransaction, StartTransaction}
import org.tupol.bankka.server.ClientActor.ClientResult

import scala.util.Try

class ClientActorSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike with Inside {

  import testKit.internalSystem.executionContext

  implicit val logger = LoggerFactory.getLogger(this.getClass)

  "Creation and retrieval" should {
    val clientId       = ClientId()
    val clientName     = "gigi"
    val expectedClient = Client(id = clientId, name = clientName, accounts = Map(), active = true)

    "succeed" in {
      val bankDao          = new BankDao(new InMemoryClientDao, new InMemoryAccountDao(), new InMemoryTransactionDao)
      val replyClientProbe = createTestProbe[ClientActor.ClientResponse]
      val testClient1      = spawn(ClientActor(clientId, bankDao, 100))
      testClient1 ! ClientActor.CreateClient(clientName, replyClientProbe.ref)
      val clientCreatedResponse = replyClientProbe.receiveMessage()
      clientCreatedResponse shouldBe a[ClientResult]
      inside(clientCreatedResponse) { case ClientResult(client) => client shouldBe expectedClient }
      testClient1 ! ClientActor.Get(replyClientProbe.ref)
      val clientFoundResponse = replyClientProbe.receiveMessage()
      inside(clientFoundResponse) { case ClientResult(client) => client shouldBe expectedClient }
    }
    "fail if the client was not created yet" in {
      val bankDao          = new BankDao(new InMemoryClientDao, new InMemoryAccountDao(), new InMemoryTransactionDao)
      val replyClientProbe = createTestProbe[ClientActor.ClientResponse]
      val testClient1      = spawn(ClientActor(clientId, bankDao, 100))
      testClient1 ! ClientActor.Get(replyClientProbe.ref)
      replyClientProbe.expectNoMessage()
    }
  }

  "Transaction between own accounts" should {
    val clientId   = ClientId()
    val clientName = "gigi"
    "succeed" in {
      val bankDao               = new BankDao(new InMemoryClientDao, new InMemoryAccountDao(), new InMemoryTransactionDao)
      val replyClientProbe      = createTestProbe[ClientActor.ClientResponse]
      val replyAccountProbe     = createTestProbe[ClientActor.AccountResponse]
      val replyTransactionProbe = createTestProbe[Try[ClientActor.TransactionResponse]]
      val testClient1           = spawn(ClientActor(clientId, bankDao, 100))
      testClient1 ! ClientActor.CreateClient(clientName, replyClientProbe.ref)
      testClient1 ! ClientActor.CreateAccount(0, 1000, replyAccountProbe.ref)
      val account1CreatedResponse = replyAccountProbe.receiveMessage()
      testClient1 ! ClientActor.CreateAccount(0, 0, replyAccountProbe.ref)
      val account2CreatedResponse = replyAccountProbe.receiveMessage()

      testClient1 ! ClientActor.OrderPayment(
        account1CreatedResponse.accountId,
        account2CreatedResponse.accountId,
        100,
        replyTransactionProbe.ref
      )
      val transactionResponse = replyTransactionProbe.receiveMessage()

      val expectedAccount1 = account1CreatedResponse.client.account(account1CreatedResponse.accountId).get.debit(100)
      val expectedAccount2 = account2CreatedResponse.client.account(account2CreatedResponse.accountId).get.credit(100)

      transactionResponse.get.client.account(account1CreatedResponse.accountId).get shouldBe expectedAccount1
      transactionResponse.get.client.account(account2CreatedResponse.accountId).get shouldBe expectedAccount2
      inside(transactionResponse.get.transaction) {
        case CompletedTransaction(_, from, to, amount, createdAt, completedAt) =>
          from shouldBe expectedAccount1.id
          to shouldBe expectedAccount2.id
          amount shouldBe 100
      }
      bankDao.transactionDao.findAll.futureValue.head shouldBe transactionResponse.get.transaction
    }

    "fail due to lack of funds" in {
      val bankDao               = new BankDao(new InMemoryClientDao, new InMemoryAccountDao(), new InMemoryTransactionDao)
      val replyClientProbe      = createTestProbe[ClientActor.ClientResponse]
      val replyAccountProbe     = createTestProbe[ClientActor.AccountResponse]
      val replyTransactionProbe = createTestProbe[Try[ClientActor.TransactionResponse]]
      val testClient1           = spawn(ClientActor(clientId, bankDao, 100))
      testClient1 ! ClientActor.CreateClient(clientName, replyClientProbe.ref)
      testClient1 ! ClientActor.CreateAccount(0, 0, replyAccountProbe.ref)
      val account1CreatedResponse = replyAccountProbe.receiveMessage()
      testClient1 ! ClientActor.CreateAccount(0, 0, replyAccountProbe.ref)
      val account2CreatedResponse = replyAccountProbe.receiveMessage()

      testClient1 ! ClientActor.OrderPayment(
        account1CreatedResponse.accountId,
        account2CreatedResponse.accountId,
        100,
        replyTransactionProbe.ref
      )
      val transactionResponse = replyTransactionProbe.receiveMessage()
      a[ClientActor.TransactionError] shouldBe thrownBy(transactionResponse.get)
      bankDao.transactionDao.findAll.futureValue.isEmpty shouldBe true
    }

    "fail if amount is 0" in {
      val bankDao               = new BankDao(new InMemoryClientDao, new InMemoryAccountDao(), new InMemoryTransactionDao)
      val replyClientProbe      = createTestProbe[ClientActor.ClientResponse]
      val replyAccountProbe     = createTestProbe[ClientActor.AccountResponse]
      val replyTransactionProbe = createTestProbe[Try[ClientActor.TransactionResponse]]
      val testClient1           = spawn(ClientActor(clientId, bankDao, 100))
      testClient1 ! ClientActor.CreateClient(clientName, replyClientProbe.ref)
      testClient1 ! ClientActor.CreateAccount(0, 0, replyAccountProbe.ref)
      val account1CreatedResponse = replyAccountProbe.receiveMessage()
      testClient1 ! ClientActor.CreateAccount(0, 0, replyAccountProbe.ref)
      val account2CreatedResponse = replyAccountProbe.receiveMessage()

      testClient1 ! ClientActor.OrderPayment(
        account1CreatedResponse.accountId,
        account2CreatedResponse.accountId,
        0,
        replyTransactionProbe.ref
      )
      val transactionResponse = replyTransactionProbe.receiveMessage()
      a[ClientActor.TransactionError] shouldBe thrownBy(transactionResponse.get)
      bankDao.transactionDao.findAll.futureValue.isEmpty shouldBe true
    }

    "fail if source and target accounts are the same " in {

      val bankDao               = new BankDao(new InMemoryClientDao, new InMemoryAccountDao(), new InMemoryTransactionDao)
      val replyClientProbe      = createTestProbe[ClientActor.ClientResponse]
      val replyAccountProbe     = createTestProbe[ClientActor.AccountResponse]
      val replyTransactionProbe = createTestProbe[Try[ClientActor.TransactionResponse]]
      val testClient1           = spawn(ClientActor(clientId, bankDao, 100))
      testClient1 ! ClientActor.CreateClient(clientName, replyClientProbe.ref)
      testClient1 ! ClientActor.CreateAccount(0, 1000, replyAccountProbe.ref)
      val account1CreatedResponse = replyAccountProbe.receiveMessage()
      testClient1 ! ClientActor.CreateAccount(0, 0, replyAccountProbe.ref)
      val account2CreatedResponse = replyAccountProbe.receiveMessage()

      testClient1 ! ClientActor.OrderPayment(
        account1CreatedResponse.accountId,
        account1CreatedResponse.accountId,
        100,
        replyTransactionProbe.ref
      )
      val transactionResponse = replyTransactionProbe.receiveMessage()
      a[ClientActor.TransactionError] shouldBe thrownBy(transactionResponse.get)
      bankDao.transactionDao.findAll.futureValue.isEmpty shouldBe true
    }

    "fail if source account does not belong to the client " in {

      val bankDao               = new BankDao(new InMemoryClientDao, new InMemoryAccountDao(), new InMemoryTransactionDao)
      val clientId              = ClientId()
      val replyClientProbe      = createTestProbe[ClientActor.ClientResponse]
      val replyAccountProbe     = createTestProbe[ClientActor.AccountResponse]
      val replyTransactionProbe = createTestProbe[Try[ClientActor.TransactionResponse]]
      val testClient1           = spawn(ClientActor(clientId, bankDao, 100))
      testClient1 ! ClientActor.CreateClient("gigi", replyClientProbe.ref)
      testClient1 ! ClientActor.CreateAccount(0, 1000, replyAccountProbe.ref)
      val account1CreatedResponse = replyAccountProbe.receiveMessage()
      testClient1 ! ClientActor.CreateAccount(0, 0, replyAccountProbe.ref)

      testClient1 ! ClientActor.OrderPayment(
        AccountId(),
        account1CreatedResponse.accountId,
        100,
        replyTransactionProbe.ref
      )
      val transactionResponse = replyTransactionProbe.receiveMessage()
      a[ClientActor.TransactionError] shouldBe thrownBy(transactionResponse.get)
      bankDao.transactionDao.findAll.futureValue.isEmpty shouldBe true
    }

  }

  "Payment order" should {
    val clientId      = ClientId()
    val targetAccount = AccountId()
    "succeed" in {
      val bankDao               = new BankDao(new InMemoryClientDao, new InMemoryAccountDao(), new InMemoryTransactionDao)
      val replyClientProbe      = createTestProbe[ClientActor.ClientResponse]
      val replyAccountProbe     = createTestProbe[ClientActor.AccountResponse]
      val replyTransactionProbe = createTestProbe[Try[ClientActor.TransactionResponse]]
      val testClient1           = spawn(ClientActor(clientId, bankDao, 100))
      testClient1 ! ClientActor.CreateClient("gigi", replyClientProbe.ref)
      testClient1 ! ClientActor.CreateAccount(0, 1000, replyAccountProbe.ref)
      val account1CreatedResponse = replyAccountProbe.receiveMessage()

      testClient1 ! ClientActor.OrderPayment(
        account1CreatedResponse.accountId,
        targetAccount,
        100,
        replyTransactionProbe.ref
      )
      val transactionResponse = replyTransactionProbe.receiveMessage()

      val expectedAccount1 = account1CreatedResponse.client.account(account1CreatedResponse.accountId).get.debit(100)

      transactionResponse.get.client.account(account1CreatedResponse.accountId).get shouldBe expectedAccount1

      inside(transactionResponse.get.transaction) {
        case StartTransaction(_, from, to, amount) =>
          from shouldBe expectedAccount1.id
          to shouldBe targetAccount
          amount shouldBe 100
      }
      bankDao.transactionDao.findAll.futureValue.head shouldBe transactionResponse.get.transaction
    }
  }

  "E2E transfer" should {
    val clientId1 = ClientId()
    val clientId2 = ClientId()
    val amount    = 100
    "succeed" in {
      val bankDao                = new BankDao(new InMemoryClientDao, new InMemoryAccountDao(), new InMemoryTransactionDao)
      val replyClientProbe1      = createTestProbe[ClientActor.ClientResponse]
      val replyAccountProbe1     = createTestProbe[ClientActor.AccountResponse]
      val replyTransactionProbe1 = createTestProbe[Try[ClientActor.TransactionResponse]]

      val replyClientProbe2      = createTestProbe[ClientActor.ClientResponse]
      val replyAccountProbe2     = createTestProbe[ClientActor.AccountResponse]
      val replyTransactionProbe2 = createTestProbe[Try[ClientActor.TransactionResponse]]

      val testClient1 = spawn(ClientActor(clientId1, bankDao, 100))
      testClient1 ! ClientActor.CreateClient("gigi", replyClientProbe1.ref)
      testClient1 ! ClientActor.CreateAccount(0, 1000, replyAccountProbe1.ref)
      val account1CreatedResponse = replyAccountProbe1.receiveMessage()
      val account1                = account1CreatedResponse.client.account(account1CreatedResponse.accountId).get

      val testClient2 = spawn(ClientActor(clientId2, bankDao, 100))
      testClient2 ! ClientActor.CreateClient("mini", replyClientProbe2.ref)
      testClient2 ! ClientActor.CreateAccount(0, 0, replyAccountProbe2.ref)
      val account2CreatedResponse = replyAccountProbe2.receiveMessage()
      val account2                = account2CreatedResponse.client.account(account2CreatedResponse.accountId).get

      testClient1 ! ClientActor.OrderPayment(
        account1.id,
        account2.id,
        amount,
        replyTransactionProbe1.ref
      )
      val transactionResponse1 = replyTransactionProbe1.receiveMessage()
      val expectedAccount1     = account1.debit(amount)

      transactionResponse1.get.client.account(account1.id).get shouldBe expectedAccount1
      inside(transactionResponse1.get.transaction) {
        case StartTransaction(_, from, to, amount) =>
          from shouldBe account1CreatedResponse.accountId
          to shouldBe account2CreatedResponse.accountId
          amount shouldBe amount
      }
      bankDao.transactionDao.findAll.futureValue.head shouldBe transactionResponse1.get.transaction

      testClient2 ! ClientActor.ReceivePayment(transactionResponse1.get.transaction, replyTransactionProbe2.ref)

      val transactionResponse2 = replyTransactionProbe2.receiveMessage()
      val expectedAccount2 =
        account2CreatedResponse.client.account(account2CreatedResponse.accountId).get.credit(amount)

      transactionResponse2.get.client.account(account2.id).get shouldBe expectedAccount2
      inside(transactionResponse2.get.transaction) {
        case CompletedTransaction(_, from, to, amount, createdAt, completedAt) =>
          from shouldBe account1.id
          to shouldBe account2.id
          amount shouldBe amount
          completedAt.isDefined shouldBe true
      }
      bankDao.transactionDao.findAll.futureValue.head shouldBe transactionResponse1.get.transaction

    }
  }
}
