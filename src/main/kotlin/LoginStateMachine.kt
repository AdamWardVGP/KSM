package org.example

import org.example.AppStates.*
import org.example.AppEvents.*

sealed class AppStates {
    object Uninitialized : AppStates()
    object RequestEula : AppStates()
    object ExitApp : AppStates()
    sealed class RequestLogin : AppStates() {
        class Uninitialized : RequestLogin()
        class LoginFailed(reason: LoginFailureReason) : RequestLogin()
    }
    object GoToMain : AppStates()
}

sealed class LoginFailureReason {
    object InvalidEmail : LoginFailureReason()
    object InvalidPassword : LoginFailureReason()
}

sealed class AppEvents {
    object EulaOutOfDate : AppEvents()
    object EulaAccepted : AppEvents()
    object EulaDenied : AppEvents()
    object LoginSuccess : AppEvents()
    data class LoginFailed(val reason: LoginFailureReason) : AppEvents()
}

val appStartStateMachine = stateMachine {

    state<Uninitialized> {
        on<EulaOutOfDate>() transitionTo { AppStates.RequestEula() }
        on<EulaAccepted>() transitionTo { RequestLogin() }
    }

    state<RequestEula> {
        on<EulaAccepted>() transitionTo { RequestLogin.Uninitialized }
        on<EulaDenied>() transitionTo { ExitApp }
    }

    state<RequestLogin> {
        on<LoginSuccess>() transitionTo { GoToMain }
        on<LoginFailed>() transitionTo { event -> RequestLogin.LoginFailed(event.reason) }
    }

}