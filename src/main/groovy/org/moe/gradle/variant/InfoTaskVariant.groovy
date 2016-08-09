/*
Copyright 2014-2016 Intel Corporation

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package org.moe.gradle.variant


class InfoTaskVariant {
    public static final String SDKS_NAME = "Sdks"
    public static final String IDENTITIES_NAME = "Identities"
    public static final String PROFILES_NAME = "Profiles"

    /*
    Predefined info.
     */
    private static final InfoTaskVariant SHOW_SDKS = new InfoTaskVariant(SDKS_NAME)
    private static final InfoTaskVariant SHOW_IDENTITIES = new InfoTaskVariant(IDENTITIES_NAME)
    private static final InfoTaskVariant SHOW_PROFILES = new InfoTaskVariant(PROFILES_NAME)

    private static final List<InfoTaskVariant> VARIANTS = [SHOW_SDKS, SHOW_IDENTITIES, SHOW_PROFILES]

    /**
     * Returns a list of all available InfoTaskVariant.
     * @return list of all available InfoTaskVariant
     */
    public static List<InfoTaskVariant> getAll() {
        new ArrayList<InfoTaskVariant>(VARIANTS)
    }

    /**
     * Get a variant by name.
     * @param name variant name
     * @return variant or null
     */
    public static InfoTaskVariant getByName(String name) {
        for (InfoTaskVariant variant in VARIANTS) {
            if (variant.getName().equalsIgnoreCase(name)) {
                return variant
            }
        }
        return null
    }

    final String name

    private InfoTaskVariant(String name) {
        this.name = name
    }
}
