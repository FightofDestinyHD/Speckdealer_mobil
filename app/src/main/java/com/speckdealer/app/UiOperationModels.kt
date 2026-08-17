package com.speckdealer.app

sealed interface UiOperationState {
	object Idle : UiOperationState
	object Loading : UiOperationState
	object Saving : UiOperationState
	data class Success(val message: String) : UiOperationState
	data class Error(val message: String) : UiOperationState
}

sealed class OperationResult<out T> {
	data class Success<T>(val value: T) : OperationResult<T>()
	data class Error(val message: String, val cause: Throwable? = null) : OperationResult<Nothing>()
}
