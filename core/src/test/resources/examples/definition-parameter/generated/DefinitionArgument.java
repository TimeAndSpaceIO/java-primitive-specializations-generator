



void myFirstMethodPreconditionsChecks(String arg1, String arg2) {
    Preconditions.checkNonNull(arg1);
    Preconditions.checkNonNull(arg2);
}

void myOtherMethodPreconditionsChecks(MyClass my) {
    Preconditions.checkNonNull(my);
}




void myFirstMethodAssertChecks(String arg1, String arg2) {
    assert arg1 != null;
    assert arg2 != null;
}

void myOtherMethodAssertChecks(MyClass my) {
    assert my != null;
}




void myFirstMethodNoChecks(String arg1, String arg2) {
}

void myOtherMethodNoChecks(MyClass my) {
}

