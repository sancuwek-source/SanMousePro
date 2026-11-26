#include <jni.h>
#include <fcntl.h>
#include <unistd.h>
#include <sys/ioctl.h>
#include <linux/input.h>

/* Buka /dev/input/eventX dan "grab" eksklusif (EVIOCGRAB) supaya event
   mouse/keyboard tidak bocor ke sistem Android. */
JNIEXPORT jint JNICALL
Java_com_san_mousepro_NativeGrab_openGrab(JNIEnv *env, jclass c, jstring path, jboolean grab) {
    const char *p = (*env)->GetStringUTFChars(env, path, 0);
    int fd = open(p, O_RDONLY | O_CLOEXEC);
    (*env)->ReleaseStringUTFChars(env, path, p);
    if (fd < 0) return -1;
    if (grab) ioctl(fd, EVIOCGRAB, (void *)1);
    return fd;
}

JNIEXPORT void JNICALL
Java_com_san_mousepro_NativeGrab_setGrab(JNIEnv *env, jclass c, jint fd, jboolean on) {
    ioctl(fd, EVIOCGRAB, (void *)(long)(on ? 1 : 0));
}
