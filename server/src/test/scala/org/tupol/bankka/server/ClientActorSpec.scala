package org.tupol.bankka.server

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import org.scalatest.Inside
import org.scalatest.wordspec.AnyWordSpecLike
import org.slf4j.LoggerFactory
import org.tupol.bankka.data.dao.BankDao.InMemoryBankDao
import org.tupol.bankka.data.model.{AccountId, Client, ClientId, CompletedTransaction, StartTransaction}
import org.tupol.bankka.server.ClientActor.{AccountResult, ClientException, ClientResult, TransactionResult}

class ClientActorSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike with Inside {

  import testKit.internalSystem.executionContext

  implicit val logger = LoggerFactory.getLogger(this.getClass)

  "Creation and retrieval" should {
    val clientId       = ClientId()
    val clientName     = "gigi"
    val expectedClient = Client(id = clientId, name = clientName, accounts = Map(), active = true)

    "succeed" in {
      val bankDao          = InMemoryBankDao
      val replyClientProbe = createTestProbe[ClientActor.ClientResponse]
      val testClient      = spawn(ClientActor(clientId, bankDao, 100))
      testClient ! ClientActor.CreateClient(clientName, replyClientProbe.ref)
      val clientCreatedResponse = replyClientProbe.receiveMessage()
      clientCreatedResponse shouldBe a[ClientResult]
      inside(clientCreatedResponse) { case ClientResult(client) => client shouldBe expectedClient }
      testClient ! ClientActor.GetClient(replyClientProbe.ref)
      val clientFoundResponse = replyClientProbe.receiveMessage()
      inside(clientFoundResponse) { case ClientResult(client) => client shouldBe expectedClient }
    }
    "fail if the client was not created yet" in {
      val bankDao          = InMemoryBankDao
      val replyClientProbe = createTestProbe[ClientActor.ClientResponse]
      val testClient      = spawn(ClientActor(clientId, bankDao, 100))
      testClient ! ClientActor.GetClient(replyClientProbe.ref)
      replyClientProbe.expectNoMessage()
    }
  }

  "Activation and deactivation" should {
    val clientId       = ClientId()
    val clientName     = "gigi"

      val bankDao          = InMemoryBankDao
      val replyClientProbe = createTestProbe[ClientActor.ClientResponse]
      val testClient       = spawn(ClientActor(clientId, bankDao, 100))
      testClient ! ClientActor.CreateClient(clientName, replyClientProbe.ref)
      replyClientProbe.expectMessageType[ClientResult]

      "activation fails" in {
        testClient ! ClientActor.ActivateClient(replyClientProbe.ref)
        val activationResponse = replyClientProbe.receiveMessage()
        activationResponse shouldBe a [ClientException]
      }
      "deactivation succeeds" in {
        testClient ! ClientActor.DeactivateClient(replyClientProbe.ref)
        val deactivationResponse = replyClientProbe.receiveMessage()
        inside(deactivationResponse) { case ClientResult(client) => client.active shouldBe false }
      }
      "deactivation fails a second time" in {
        testClient ! ClientActor.DeactivateClient(replyClientProbe.ref)
        val deactivationResponse = replyClientProbe.receiveMessage()
        deactivationResponse shouldBe a [ClientException]
      }
      "activation succeeds" in {
        testClient ! ClientActor.ActivateClient(replyClientProbe.ref)
        val activationResponse = replyClientProbe.receiveMessage()
        inside(activationResponse) { case ClientResult(client) => client.active shouldBe true }
      }
  }

  "Transaction between own accounts" should {
    val clientId   = ClientId()
    val clientName = "gigi"
    "succeed" in {
      val bankDao               = InMemoryBankDao
      val replyClientProbe      = createTestProbe[ClientActor.ClientResponse]
      val replyAccountProbe     = createTestProbe[ClientActor.AccountResponse]
      val replyTransactionProbe = createTestProbe[ClientActor.TransactionResponse]
      val testClient1           = spawn(ClientActor(clientId, bankDao, 100))
      testClient1 ! ClientActor.CreateClient(clientName, replyClientProbe.ref)
      testClient1 ! ClientActor.CreateAccount(0, 1000, replyAccountProbe.ref)
      val account1CreatedResponse = replyAccountProbe.receiveMessage()
      val account1Result = account1CreatedResponse match { case r: AccountResult => r }
      testClient1 ! ClientActor.CreateAccount(0, 0, replyAccountProbe.ref)
      val account2CreatedResponse = replyAccountProbe.receiveMessage()
      val account2Result = account2CreatedResponse match { case r: AccountResult => r }

      testClient1 ! ClientActor.OrderPayment(
        account1Result.accountId,
        account2Result.accountId,
        100,
        replyTransactionProbe.ref
      )
      val transactionResponse = replyTransactionProbe.receiveMessage()
      val transactionResult = transactionResponse match { case r: TransactionResult => r }

      val expectedAccount1 = account1Result.client.account(account1Result.accountId).get.debit(100)
      val expectedAccount2 = account2Result.client.account(account2Result.accountId).get.credit(100)

      transactionResult.client.account(account1Result.accountId).get shouldBe expectedAccount1
      transactionResult.client.account(account2Result.accountId).get shouldBe expectedAccount2
      inside(transactionResult.transaction) {
        case CompletedTransaction(_, from, to, amount, createdAt, completedAt) =>
          from shouldBe expectedAccount1.id
          to shouldBe expectedAccount2.id
          amount shouldBe 100
      }
      bankDao.transactionDao.findAll.futureValue.head shouldBe transactionResult.transaction
    }

    "fail due to lack of funds" in {
      val bankDao               = InMemoryBankDao
      val replyClientProbe      = createTestProbe[ClientActor.ClientResponse]
      val replyAccountProbe     = createTestProbe[ClientActor.AccountResponse]
      val replyTransactionProbe = createTestProbe[ClientActor.TransactionResponse]
      val testClient1           = spawn(ClientActor(clientId, bankDao, 100))
      testClient1 ! ClientActor.CreateClient(clientName, replyClientProbe.ref)
      testClient1 ! ClientActor.CreateAccount(0, 0, replyAccountProbe.ref)
      val account1CreatedResponse = replyAccountProbe.receiveMessage()
      val account1Result = account1CreatedResponse match { case r: AccountResult => r }
      testClient1 ! ClientActor.CreateAccount(0, 0, replyAccountProbe.ref)
      val account2CreatedResponse = replyAccountProbe.receiveMessage()
      val account2Result = account2CreatedResponse match { case r: AccountResult => r }

      testClient1 ! ClientActor.OrderPayment(
        account1Result.accountId,
        account2Result.accountId,
        100,
        replyTransactionProbe.ref
      )
      val transactionResponse = replyTransactionProbe.receiveMessage()
      transactionResponse shouldBe a [ClientActor.TransactionException]
      bankDao.transactionDao.findAll.futureValue.isEmpty shouldBe true
    }

    "fail if amount is 0" in {
      val bankDao               = InMemoryBankDao
      val replyClientProbe      = createTestProbe[ClientActor.ClientResponse]
      val replyAccountProbe     = createTestProbe[ClientActor.AccountResponse]
      val replyTransactionProbe = createTestProbe[ClientActor.TransactionResponse]
      val testClient1           = spawn(ClientActor(clientId, bankDao, 100))
      testClient1 ! ClientActor.CreateClient(clientName, replyClientProbe.ref)
      testClient1 ! ClientActor.CreateAccount(0, 0, replyAccountProbe.ref)
      val account1CreatedResponse = replyAccountProbe.receiveMessage()
      val account1Result = account1CreatedResponse match { case r: AccountResult => r }
      testClient1 ! ClientActor.CreateAccount(0, 0, replyAccountProbe.ref)
      val account2CreatedResponse = replyAccountProbe.receiveMessage()
      val account2Result = account2CreatedResponse match { case r: AccountResult => r }

      testClient1 ! ClientActor.OrderPayment(
        account1Result.accountId,
        account2Result.accountId,
        0,
        replyTransactionProbe.ref
      )
      val transactionResponse = replyTransactionProbe.receiveMessage()
      transactionResponse shouldBe a [ClientActor.TransactionException]
      bankDao.transactionDao.findAll.futureValue.isEmpty shouldBe true
    }

    "fail if source and target accounts are the same " in {

      val bankDao               = InMemoryBankDao
      val replyClientProbe      = createTestProbe[ClientActor.ClientResponse]
      val replyAccountProbe     = createTestProbe[ClientActor.AccountResponse]
      val replyTransactionProbe = createTestProbe[ClientActor.TransactionResponse]
      val testClient1           = spawn(ClientActor(clientId, bankDao, 100))
      testClient1 ! ClientActor.CreateClient(clientName, replyClientProbe.ref)
      testClient1 ! ClientActor.CreateAccount(0, 1000, replyAccountProbe.ref)
      val account1CreatedResponse = replyAccountProbe.receiveMessage()
      val account1Result = account1CreatedResponse match { case r: AccountResult => r }
      testClient1 ! ClientActor.CreateAccount(0, 0, replyAccountProbe.ref)
      val account2CreatedResponse = replyAccountProbe.receiveMessage()

      testClient1 ! ClientActor.OrderPayment(
        account1Result.accountId,
        account1Result.accountId,
        100,
        replyTransactionProbe.ref
      )
      val transactionResponse = replyTransactionProbe.receiveMessage()
      transactionResponse shouldBe a [ClientActor.TransactionException]
      bankDao.transactionDao.findAll.futureValue.isEmpty shouldBe true
    }

    "fail if source account does not belong to the client " in {

      val bankDao               = InMemoryBankDao
      val clientId              = ClientId()
      val replyClientProbe      = createTestProbe[ClientActor.ClientResponse]
      val replyAccountProbe     = createTestProbe[ClientActor.AccountResponse]
      val replyTransactionProbe = createTestProbe[ClientActor.TransactionResponse]
      val testClient1           = spawn(ClientActor(clientId, bankDao, 100))
      testClient1 ! ClientActor.CreateClient("gigi", replyClientProbe.ref)
      testClient1 ! ClientActor.CreateAccount(0, 1000, replyAccountProbe.ref)
      val account1CreatedResponse = replyAccountProbe.receiveMessage()
      val account1Result = account1CreatedResponse match { case r: AccountResult => r }
      testClient1 ! ClientActor.CreateAccount(0, 0, replyAccountProbe.ref)

      testClient1 ! ClientActor.OrderPayment(
        AccountId(),
        account1Result.accountId,
        100,
        replyTransactionProbe.ref
      )
      val transactionResponse = replyTransactionProbe.receiveMessage()
      transactionResponse shouldBe a [ClientActor.TransactionException]
      bankDao.transactionDao.findAll.futureValue.isEmpty shouldBe true
    }

  }

  "Payment order" should {
    val clientId      = ClientId()
    val targetAccount = AccountId()
    "succeed" in {
      val bankDao               = InMemoryBankDao
      val replyClientProbe      = createTestProbe[ClientActor.ClientResponse]
      val replyAccountProbe     = createTestProbe[ClientActor.AccountResponse]
      val replyTransactionProbe = createTestProbe[ClientActor.TransactionResponse]
      val testClient1           = spawn(ClientActor(clientId, bankDao, 100))
      testClient1 ! ClientActor.CreateClient("gigi", replyClientProbe.ref)
      testClient1 ! ClientActor.CreateAccount(0, 1000, replyAccountProbe.ref)
      val account1CreatedResponse = replyAccountProbe.receiveMessage()
      val account1Result = account1CreatedResponse match { case r: AccountResult => r }

      testClient1 ! ClientActor.OrderPayment(
        account1Result.accountId,
        targetAccount,
        100,
        replyTransactionProbe.ref
      )
      val transactionResponse = replyTransactionProbe.receiveMessage()
      val transactionResult = transactionResponse match { case r: TransactionResult => r }

      val expectedAccount1 = account1Result.client.account(account1Result.accountId).get.debit(100)

      transactionResult.client.account(account1Result.accountId).get shouldBe expectedAccount1

      inside(transactionResult.transaction) {
        case StartTransaction(_, from, to, amount) =>
          from shouldBe expectedAccount1.id
          to shouldBe targetAccount
          amount shouldBe 100
      }
      bankDao.transactionDao.findAll.futureValue.head shouldBe transactionResult.transaction
    }
  }

  "E2E transfer" should {
    val clientId1 = ClientId()
    val clientId2 = ClientId()
    val amount    = 100
    "succeed" in {
      val bankDao                = InMemoryBankDao
      val replyClientProbe1      = createTestProbe[ClientActor.ClientResponse]
      val replyAccountProbe1     = createTestProbe[ClientActor.AccountResponse]
      val replyTransactionProbe1 = createTestProbe[ClientActor.TransactionResponse]

      val replyClientProbe2      = createTestProbe[ClientActor.ClientResponse]
      val replyAccountProbe2     = createTestProbe[ClientActor.AccountResponse]
      val replyTransactionProbe2 = createTestProbe[ClientActor.TransactionResponse]

      val testClient1 = spawn(ClientActor(clientId1, bankDao, 100))
      testClient1 ! ClientActor.CreateClient("gigi", replyClientProbe1.ref)
      testClient1 ! ClientActor.CreateAccount(0, 1000, replyAccountProbe1.ref)
      val account1CreatedResponse = replyAccountProbe1.receiveMessage()
      val account1Result = account1CreatedResponse match { case r: AccountResult => r }
      val account1                = account1Result.client.account(account1Result.accountId).get

      val testClient2 = spawn(ClientActor(clientId2, bankDao, 100))
      testClient2 ! ClientActor.CreateClient("mini", replyClientProbe2.ref)
      testClient2 ! ClientActor.CreateAccount(0, 0, replyAccountProbe2.ref)
      val account2CreatedResponse = replyAccountProbe2.receiveMessage()
      val account2Result = account2CreatedResponse match { case r: AccountResult => r }
      val account2                = account2Result.client.account(account2Result.accountId).get

      testClient1 ! ClientActor.OrderPayment(
        account1.id,
        account2.id,
        amount,
        replyTransactionProbe1.ref
      )
      val transactionResponse1 = replyTransactionProbe1.receiveMessage()
      val transactionResult1 = transactionResponse1 match { case r: TransactionResult => r }
      val expectedAccount1     = account1.debit(amount)

      transactionResult1.client.account(account1.id).get shouldBe expectedAccount1
      inside(transactionResult1.transaction) {
        case StartTransaction(_, from, to, amount) =>
          from shouldBe account1Result.accountId
          to shouldBe account2Result.accountId
          amount shouldBe amount
      }
      bankDao.transactionDao.findAll.futureValue.head shouldBe transactionResult1.transaction

      testClient2 ! ClientActor.ReceivePayment(transactionResult1.transaction, replyTransactionProbe2.ref)

      val transactionResponse2 = replyTransactionProbe2.receiveMessage()
      val transactionResult2 = transactionResponse2 match { case r: TransactionResult => r }
      val expectedAccount2 =
        account2Result.client.account(account2Result.accountId).get.credit(amount)

      transactionResult2.client.account(account2.id).get shouldBe expectedAccount2
      inside(transactionResult2.transaction) {
        case CompletedTransaction(_, from, to, amount, createdAt, completedAt) =>
          from shouldBe account1.id
          to shouldBe account2.id
          amount shouldBe amount
          completedAt.isDefined shouldBe true
      }
      bankDao.transactionDao.findAll.futureValue.head shouldBe transactionResult1.transaction
    }
  }
}
