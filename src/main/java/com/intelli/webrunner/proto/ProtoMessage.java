package com.intelli.webrunner.proto;

import java.util.ArrayList;
import java.util.List;

final class ProtoMessage {

	String name;
	String fullName;
	String packageName;
	String displayName;
	List<ProtoField> fields = new ArrayList<>();
}
