package com.catchmystm.backend.dto;

public record ApiResponse<T>(
		boolean success,
	    String message,
	    T data
		) {

}
