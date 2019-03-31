
/* with Preconditions|Assert|No parameterChecks */

/* define checkNonNull x */
/* if Preconditions parameterChecks //
    Preconditions.checkNonNull(x);
// elif Assert parameterChecks //
    assert x != null;
// endif */ /* comment // if parameterChecks=No, not emitting anything //*/
/* enddefine */

void myFirstMethodPreconditionsChecks(String arg1, String arg2) {
    /* checkNonNull arg1 */
    /* checkNonNull arg2 */
}

void myOtherMethodPreconditionsChecks(MyClass my) {
    /* checkNonNull my */
}

/* endwith */