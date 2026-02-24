package com.intelli.webrunner.state;

import java.util.ArrayList;
import java.util.List;

public class WebrunnerState {
    public List<NodeState> nodes = new ArrayList<>();
    public List<RequestDetailsState> requestDetails = new ArrayList<>();
    public List<RequestStatusState> requestStatuses = new ArrayList<>();
    public List<ChainState> chainStates = new ArrayList<>();
    public List<HeaderPresetState> headerPresets = new ArrayList<>();
}
