package coffee.adammakes.ksm

import app.cash.turbine.test
import coffee.adammakes.ksm.AppEvents.*
import coffee.adammakes.ksm.AppStates.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class AppStartStateMachineTest {

  private val testDispatcher = StandardTestDispatcher()
  private val testScope = TestScope(testDispatcher)

  private fun newMachine(initial: AppStates) =
    stateMachine<AppStates, AppEvents> {
      initialState = initial
      dispatchedOn = testScope.backgroundScope

      state<Uninitialized> {
        on<EulaOutOfDate>() transitionTo RequestEula
        on<EulaAccepted>() transitionTo Login.CredentialsPrompt
      }

      state<RequestEula> {
        on<EulaAccepted>() transitionTo Login.CredentialsPrompt
        on<EulaDenied>() transitionTo ExitApp
      }

      state<Login> {
        on<LoginSuccess>() transitionTo GoToMain
        on<LoginFailed>() transitionTo Login.LoginFailed

        state<Login.CredentialsPrompt> {}
        state<Login.LoginFailed> {}
      }
    }

  @Test
  fun `happy path reaches main`() =
    testScope.runTest {
      val fsm = newMachine(Uninitialized)

      fsm.currentState.test {
        assertEquals(Uninitialized, awaitItem())

        fsm.dispatchEvent(EulaAccepted)
        assertEquals(Login.CredentialsPrompt, awaitItem())

        fsm.dispatchEvent(LoginSuccess)
        assertEquals(GoToMain, awaitItem())

        cancelAndIgnoreRemainingEvents()
      }

      testScope.backgroundScope.cancel()
    }

  @Test
  fun `eula denial exits app`() =
    testScope.runTest {
      val fsm = newMachine(Uninitialized)

      fsm.currentState.test {
        assertEquals(Uninitialized, awaitItem())

        fsm.dispatchEvent(EulaOutOfDate)
        assertEquals(RequestEula, awaitItem())

        fsm.dispatchEvent(EulaDenied)
        assertEquals(ExitApp, awaitItem())

        cancelAndIgnoreRemainingEvents()
      }

      testScope.backgroundScope.cancel()
    }

  @Test
  fun `login failure transitions with reason`() =
    testScope.runTest {
      val fsm = newMachine(Login.CredentialsPrompt)

      fsm.currentState.test {
        assertEquals(Login.CredentialsPrompt, awaitItem())

        fsm.dispatchEvent(LoginFailed)

        val resultState = awaitItem()
        assertTrue(resultState is Login.LoginFailed)

        cancelAndIgnoreRemainingEvents()
      }

      testScope.backgroundScope.cancel()
    }
}
