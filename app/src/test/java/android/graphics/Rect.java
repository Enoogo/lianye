package android.graphics;

/**
 * Test-only functional stand-in for the android.jar stub.
 *
 * The SDK stubs shipped with unit tests (isReturnDefaultValues = true) have no-op
 * constructors and field initialisers, so Rect(0, 300, 1080, 2100) would leave every
 * field at 0 in plain JVM tests. This shadow lives on the unit-test classpath ahead of
 * android.jar and implements only the members the tests exercise. It is never packaged
 * into the APK.
 */
public final class Rect {
    public int left;
    public int top;
    public int right;
    public int bottom;

    public Rect() {
    }

    public Rect(int left, int top, int right, int bottom) {
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
    }

    public Rect(Rect other) {
        if (other != null) {
            this.left = other.left;
            this.top = other.top;
            this.right = other.right;
            this.bottom = other.bottom;
        }
    }

    public int width() {
        return right - left;
    }

    public int height() {
        return bottom - top;
    }

    public boolean intersects(int left, int top, int right, int bottom) {
        return left < this.right && right > this.left && top < this.bottom && bottom > this.top;
    }

    public boolean intersects(Rect other) {
        return other != null && intersects(other.left, other.top, other.right, other.bottom);
    }

    public void set(int left, int top, int right, int bottom) {
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
    }

    public void set(Rect other) {
        if (other != null) {
            set(other.left, other.top, other.right, other.bottom);
        }
    }

    public void setEmpty() {
        set(0, 0, 0, 0);
    }

    public boolean isEmpty() {
        return left >= right || top >= bottom;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Rect)) return false;
        Rect r = (Rect) o;
        return left == r.left && top == r.top && right == r.right && bottom == r.bottom;
    }

    @Override
    public int hashCode() {
        int result = left;
        result = 31 * result + top;
        result = 31 * result + right;
        result = 31 * result + bottom;
        return result;
    }

    @Override
    public String toString() {
        return "Rect(" + left + ", " + top + " - " + right + ", " + bottom + ")";
    }
}
