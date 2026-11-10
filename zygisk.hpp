#pragma once
#include <jni.h>
#include <sys/types.h>

namespace zygisk {
    struct AppSpecializeArgs {
        jstring nice_name;
        jstring app_data_dir;
        jint uid;
        // ... (otros campos que no usamos)
    };

    class Api {
    public:
        void setOption(int option);
    };

    class Module {
    public:
        virtual void onLoad(Api* api, JNIEnv* env) = 0;
        virtual void preAppSpecialize(AppSpecializeArgs* args) = 0;
        virtual void postAppSpecialize(const AppSpecializeArgs* args) = 0;
        virtual ~Module() = default;
    };
}

#define REGISTER_ZYGISK_MODULE(clazz) \
extern "C" __attribute__((visibility("default"))) void zygisk_module_entry(zygisk::Api *api, JNIEnv *env) { \
    static clazz module; \
    module.onLoad(api, env); \
} \
extern "C" __attribute__((visibility("default"))) void zygisk_module_pre_app_specialize(zygisk::AppSpecializeArgs *args) { \
    static clazz module; \
    module.preAppSpecialize(args); \
} \
extern "C" __attribute__((visibility("default"))) void zygisk_module_post_app_specialize(zygisk::AppSpecializeArgs *args) { \
    static clazz module; \
    module.postAppSpecialize(args); \
}