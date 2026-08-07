#include <jni.h>
#include <android/native_window.h>
#include <android/native_window_jni.h>
#include <vulkan/vulkan.h>
#include <vulkan/vulkan_android.h>

#include <mutex>
#include <string>
#include <unordered_map>
#include <vector>

namespace {

struct SurfaceState {
    ANativeWindow* window;
    VkSurfaceKHR surface;
};

std::mutex stateMutex;
VkInstance instance = VK_NULL_HANDLE;
std::unordered_map<jlong, SurfaceState> surfaces;
jlong nextSurfaceId = 1;

bool hasExtension(const char* name) {
    uint32_t count = 0;
    if (vkEnumerateInstanceExtensionProperties(nullptr, &count, nullptr) != VK_SUCCESS) {
        return false;
    }
    std::vector<VkExtensionProperties> properties(count);
    if (vkEnumerateInstanceExtensionProperties(nullptr, &count, properties.data()) != VK_SUCCESS) {
        return false;
    }
    for (const VkExtensionProperties& property : properties) {
        if (std::string(property.extensionName) == name) {
            return true;
        }
    }
    return false;
}

bool createInstance() {
    if (instance != VK_NULL_HANDLE) {
        return true;
    }
    if (!hasExtension(VK_KHR_SURFACE_EXTENSION_NAME) || !hasExtension(VK_KHR_ANDROID_SURFACE_EXTENSION_NAME)) {
        return false;
    }

    VkApplicationInfo applicationInfo{};
    applicationInfo.sType = VK_STRUCTURE_TYPE_APPLICATION_INFO;
    applicationInfo.pApplicationName = "showdown-ds";
    applicationInfo.applicationVersion = VK_MAKE_VERSION(0, 1, 0);
    applicationInfo.pEngineName = "showdown-ds";
    applicationInfo.engineVersion = VK_MAKE_VERSION(0, 1, 0);
    applicationInfo.apiVersion = VK_API_VERSION_1_0;

    const char* extensions[] = {
        VK_KHR_SURFACE_EXTENSION_NAME,
        VK_KHR_ANDROID_SURFACE_EXTENSION_NAME
    };

    VkInstanceCreateInfo createInfo{};
    createInfo.sType = VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO;
    createInfo.pApplicationInfo = &applicationInfo;
    createInfo.enabledExtensionCount = 2;
    createInfo.ppEnabledExtensionNames = extensions;
    return vkCreateInstance(&createInfo, nullptr, &instance) == VK_SUCCESS;
}

void destroySurfaces() {
    for (const auto& entry : surfaces) {
        vkDestroySurfaceKHR(instance, entry.second.surface, nullptr);
        ANativeWindow_release(entry.second.window);
    }
    surfaces.clear();
}

void destroyInstance() {
    if (instance == VK_NULL_HANDLE) {
        return;
    }
    destroySurfaces();
    vkDestroyInstance(instance, nullptr);
    instance = VK_NULL_HANDLE;
}

std::string apiVersion() {
    uint32_t version = VK_API_VERSION_1_0;
    PFN_vkEnumerateInstanceVersion enumerateInstanceVersion = reinterpret_cast<PFN_vkEnumerateInstanceVersion>(
        vkGetInstanceProcAddr(nullptr, "vkEnumerateInstanceVersion"));
    if (enumerateInstanceVersion != nullptr) {
        enumerateInstanceVersion(&version);
    }
    return "Vulkan " + std::to_string(VK_VERSION_MAJOR(version)) + "." + std::to_string(VK_VERSION_MINOR(version));
}

}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_showdown_ds_MainActivity_nativeInitializeVulkan(JNIEnv*, jclass) {
    std::lock_guard<std::mutex> lock(stateMutex);
    return createInstance() ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_showdown_ds_MainActivity_nativeReleaseVulkan(JNIEnv*, jclass) {
    std::lock_guard<std::mutex> lock(stateMutex);
    destroyInstance();
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_showdown_ds_MainActivity_nativeAttachSurface(JNIEnv* env, jclass, jobject javaSurface) {
    std::lock_guard<std::mutex> lock(stateMutex);
    if (instance == VK_NULL_HANDLE || javaSurface == nullptr) {
        return 0;
    }

    ANativeWindow* window = ANativeWindow_fromSurface(env, javaSurface);
    if (window == nullptr) {
        return 0;
    }

    PFN_vkCreateAndroidSurfaceKHR createAndroidSurface = reinterpret_cast<PFN_vkCreateAndroidSurfaceKHR>(
        vkGetInstanceProcAddr(instance, "vkCreateAndroidSurfaceKHR"));
    if (createAndroidSurface == nullptr) {
        ANativeWindow_release(window);
        return 0;
    }

    VkAndroidSurfaceCreateInfoKHR surfaceInfo{};
    surfaceInfo.sType = VK_STRUCTURE_TYPE_ANDROID_SURFACE_CREATE_INFO_KHR;
    surfaceInfo.window = window;

    VkSurfaceKHR surface = VK_NULL_HANDLE;
    if (createAndroidSurface(instance, &surfaceInfo, nullptr, &surface) != VK_SUCCESS) {
        ANativeWindow_release(window);
        return 0;
    }

    const jlong surfaceId = nextSurfaceId++;
    surfaces.emplace(surfaceId, SurfaceState{window, surface});
    return surfaceId;
}

extern "C" JNIEXPORT void JNICALL
Java_com_showdown_ds_MainActivity_nativeDetachSurface(JNIEnv*, jclass, jlong surfaceId) {
    std::lock_guard<std::mutex> lock(stateMutex);
    const auto entry = surfaces.find(surfaceId);
    if (entry == surfaces.end()) {
        return;
    }
    vkDestroySurfaceKHR(instance, entry->second.surface, nullptr);
    ANativeWindow_release(entry->second.window);
    surfaces.erase(entry);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_showdown_ds_MainActivity_nativeGetVulkanApiVersion(JNIEnv* env, jclass) {
    std::lock_guard<std::mutex> lock(stateMutex);
    return env->NewStringUTF(instance == VK_NULL_HANDLE ? "Unavailable" : apiVersion().c_str());
}

extern "C" JNIEXPORT jint JNICALL
Java_com_showdown_ds_MainActivity_nativeGetSurfaceCount(JNIEnv*, jclass) {
    std::lock_guard<std::mutex> lock(stateMutex);
    return static_cast<jint>(surfaces.size());
}
