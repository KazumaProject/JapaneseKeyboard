#include <vulkan/vulkan_core.h>
#include <dlfcn.h>
#include <stdexcept>

extern "C" __attribute__((visibility("hidden"))) void __wrap_vkGetPhysicalDeviceFeatures2(
    VkPhysicalDevice device, VkPhysicalDeviceFeatures2 * features) {
    // Keep libvulkan loaded for as long as the cached function pointer is used.
    static void * library = dlopen("libvulkan.so", RTLD_NOW | RTLD_LOCAL);
    static auto function = library ? reinterpret_cast<PFN_vkGetPhysicalDeviceFeatures2>(
        dlsym(library, "vkGetPhysicalDeviceFeatures2")) : nullptr;
    if (!function) throw std::runtime_error("The Android Vulkan loader does not support Vulkan 1.1.");
    function(device, features);
}
