
byte unwrapByte(byte v) { return v; }
byte unwrapRawByte(byte v) { return v; }
byte wrapByte(byte bits) { return bits; }


int unwrapInt(int v) { return v; }
int unwrapRawInt(int v) { return v; }
int wrapInt(int bits) { return bits; }


long unwrapLong(long v) { return v; }
long unwrapRawLong(long v) { return v; }
long wrapLong(long bits) { return bits; }


int unwrapFloat(float v) { return Float.floatToIntBits(v); }
int unwrapRawFloat(float v) { return Float.floatToRawIntBits(v); }
float wrapFloat(int bits) { return Float.intBitsToFloat(bits); }


long unwrapDouble(double v) { return Double.doubleToLongBits(v); }
long unwrapRawDouble(double v) { return Double.doubleToRawLongBits(v); }
double wrapDouble(long bits) { return Double.longBitsToDouble(bits); }

