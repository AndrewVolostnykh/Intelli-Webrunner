package com.intelli.webrunner.state;

import java.util.ArrayList;
import java.util.List;

public class ChainState {
    public String requestId;
    public List<String> requestIds = new ArrayList<>();
    public String logs;
    public String currentState;
}
