package com.monarret.backend.service;

public enum FeedHealth {
    // No successful poll of both feeds since startup
    STARTING,
    HEALTHY,
    // A feed has not been fetched successfully within StmPoller.feedStaleAfter()
    DOWN
}
