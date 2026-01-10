package org.example

import org.example.AppStates.*
import org.example.AppEvents.*

sealed class AppStates {
    object Uninitialized : AppStates()
    object RequestEula : AppStates()
    object ExitApp : AppStates()
    sealed class Login : AppStates() {
        object RequestInput : Login()
        class Failed(val reason: LoginFailureReason) : Login()
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
        on<EulaOutOfDate>() transitionTo RequestEula
        on<EulaAccepted>() transitionTo Login.RequestInput
    }

    state<RequestEula> {
        on<EulaAccepted>() transitionTo Login.RequestInput
        on<EulaDenied>() transitionTo ExitApp
    }

    state<Login> {
        on<LoginSuccess>() transitionTo GoToMain
        on<LoginFailed>() transitionWith { _, event -> Login.Failed(event.reason) }
    }

}