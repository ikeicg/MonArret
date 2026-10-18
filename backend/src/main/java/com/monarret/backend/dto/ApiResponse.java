package com.monarret.backend.dto;

public record ApiResponse<T>(
		boolean success,
	    String message,
	    T data
		) {

}
