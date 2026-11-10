#include "zygisk.hpp"
#include "server.h"
#include <pthread.h>
#include <string.h>
#include <unistd.h>
#include <android/log.h>

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, "ElTrampero", __VA_ARGS__)
#define TARGET_PKG "com.dts.freefireth"

static bool es_juego = false;

static void* hilo_servidor(void*) {
    // Esperamos 10s para que Free Fire cargue los datos en memoria
    sleep(10); 
    server_loop(); 
    return nullptr;
}

// La clase ahora hereda de zygisk::ModuleBase
class ElTramperoModule : public zygisk::ModuleBase {
public:
    void onLoad(zygisk::Api* api, JNIEnv* env) override {
        this->api = api;
        this->env = env;
    }

    void preAppSpecialize(zygisk::AppSpecializeArgs* args) override {
        // args->nice_name es una referencia a jstring
        const char* nice = args->nice_name ? env->GetStringUTFChars(args->nice_name, nullptr) : nullptr;
        es_juego = (nice && strcmp(nice, TARGET_PKG) == 0);
        if (nice) env->ReleaseStringUTFChars(args->nice_name, nice);

        if (!es_juego) {
            // Si no es Free Fire, descargar el módulo de este proceso
            api->setOption(zygisk::DLCLOSE_MODULE_LIBRARY);
        }
    }

    void postAppSpecialize(const zygisk::AppSpecializeArgs* args) override {
        if (!es_juego) return;
        LOGI("Módulo cargado en %s — iniciando socket", TARGET_PKG);
        
        pthread_t t;
        pthread_create(&t, nullptr, hilo_servidor, nullptr);
        pthread_detach(t);
    }

private:
    zygisk::Api* api;
    JNIEnv* env;
};

// El macro oficial
REGISTER_ZYGISK_MODULE(ElTramperoModule)