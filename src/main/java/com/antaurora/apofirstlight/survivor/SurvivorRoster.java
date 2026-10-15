package com.antaurora.apofirstlight.survivor;

import com.antaurora.apofirstlight.ApocalypseFirstLight;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The fixed roster of successors (docs/gameplay/survivor_death_succession_v0.md 13): sixty people, each with one full
 * name and one skin, so a face always belongs to the same person. The id is also the skin id ({@code f..} slim women,
 * {@code m..} classic men); tools/build-survivor-skins.mjs paints the skins and checks this list against its specs.
 *
 * <p>A successor is a person not yet seen in this world, in any lineage, so two players never meet the same person or
 * find the corpse of someone who is walking around. Only when everyone has been on stage does the person longest gone
 * come back. The first survivor of a lineage is never from the roster (account name or nickname, account skin).
 */
public final class SurvivorRoster {
    public record Person(String id, String first, String last, SurvivorRecord.Sex sex) {
        public String fullName() {
            return first + " " + last;
        }
    }

    static final List<Person> PEOPLE = List.of(
            person("f01", "Megan", "Kelly"),
            person("f02", "Denise", "Carter"),
            person("f03", "Elena", "Castillo"),
            person("f04", "Holly", "Jensen"),
            person("f05", "Grace", "Chen"),
            person("f06", "Rosa", "Mendez"),
            person("f07", "Janet", "Sullivan"),
            person("f08", "Tara", "Brooks"),
            person("f09", "Kim", "Nguyen"),
            person("f10", "Claire", "Morgan"),
            person("f11", "Priya", "Patel"),
            person("f12", "Ivy", "Riley"),
            person("f13", "Dana", "Hayes"),
            person("f14", "Gloria", "Ortiz"),
            person("f15", "Lena", "Fischer"),
            person("f16", "Leila", "Nasser"),
            person("f17", "Wendy", "Baker"),
            person("f18", "Angela", "Ward"),
            person("f19", "Amy", "Lin"),
            person("f20", "Sarah", "Collins"),
            person("f21", "Carmen", "Flores"),
            person("f22", "Monique", "Harris"),
            person("f23", "Fiona", "Walsh"),
            person("f24", "Anita", "Shah"),
            person("f25", "Jenna", "Turner"),
            person("f26", "Beverly", "Coleman"),
            person("f27", "Hana", "Mori"),
            person("f28", "Maria", "Gonzalez"),
            person("f29", "Rachel", "Cooper"),
            person("f30", "Keisha", "Robinson"),
            person("m01", "Jake", "Miller"),
            person("m02", "Marcus", "Reed"),
            person("m03", "Miguel", "Torres"),
            person("m04", "Sean", "O'Brien"),
            person("m05", "Andre", "Walker"),
            person("m06", "Frank", "Kowalski"),
            person("m07", "Victor", "Alvarez"),
            person("m08", "Daniel", "Tanaka"),
            person("m09", "Ryan", "Anderson"),
            person("m10", "Isaac", "Price"),
            person("m11", "Eli", "Novak"),
            person("m12", "Lucas", "Silva"),
            person("m13", "Owen", "Begay"),
            person("m14", "Hank", "Schmidt"),
            person("m15", "Leon", "Russo"),
            person("m16", "Tom", "Bennett"),
            person("m17", "Darnell", "Jackson"),
            person("m18", "Kevin", "Park"),
            person("m19", "Hector", "Morales"),
            person("m20", "Cody", "Larsen"),
            person("m21", "Walter", "Banks"),
            person("m22", "Omar", "Haddad"),
            person("m23", "Kyle", "Dawson"),
            person("m24", "Jesse", "Harjo"),
            person("m25", "Calvin", "Grant"),
            person("m26", "Gary", "Mitchell"),
            person("m27", "Jun", "Sato"),
            person("m28", "Diego", "Vargas"),
            person("m29", "Tyler", "Grady"),
            person("m30", "Malik", "Thompson"));

    private SurvivorRoster() {
    }

    private static Person person(String id, String first, String last) {
        return new Person(id, first, last, id.startsWith("f") ? SurvivorRecord.Sex.FEMALE : SurvivorRecord.Sex.MALE);
    }

    /**
     * The next successor in this world, from the successor's roll seed: a person of the rolled sex (even odds; the other
     * sex when none of it is left) whom no record of this world has used yet, and whose name no current survivor carries
     * (a first survivor's nickname may match a roster name). Called at the death, after the dead record is final.
     */
    static Person pick(SurvivorWorldData data, long seed) {
        // when each person was last on stage: the death time, or MAX while alive or waiting to be taken over
        Map<String, Long> lastSeen = new HashMap<>();
        Set<String> namesInUse = new HashSet<>();
        for (SurvivorLineage lineage : data.lineages()) {
            SurvivorRecord current = lineage.current();
            if (current != null) namesInUse.add(current.fullName().toLowerCase(Locale.ROOT));
            for (SurvivorRecord r : lineage.lives()) {
                long seen = r.state == SurvivorRecord.State.DEAD ? r.deathGameTime : Long.MAX_VALUE;
                lastSeen.merge(r.skin, seen, Math::max);
            }
        }
        SurvivorRecord.Sex sex = SurvivorNames.sex(seed);
        List<Person> fresh = fresh(sex, lastSeen, namesInUse);
        if (fresh.isEmpty()) fresh = fresh(sex == SurvivorRecord.Sex.FEMALE ? SurvivorRecord.Sex.MALE : SurvivorRecord.Sex.FEMALE,
                lastSeen, namesInUse);
        if (!fresh.isEmpty()) return fresh.get((int) Math.floorMod(SurvivorNames.stream(seed, 7), (long) fresh.size()));

        // everyone has been on stage: the one longest gone comes back (never someone alive or waiting)
        Person back = null;
        long oldest = Long.MAX_VALUE;
        for (Person person : PEOPLE) {
            long seen = lastSeen.getOrDefault(person.id(), Long.MIN_VALUE);
            if (seen < oldest && !namesInUse.contains(person.fullName().toLowerCase(Locale.ROOT))) {
                back = person;
                oldest = seen;
            }
        }
        if (back == null) back = PEOPLE.get((int) Math.floorMod(SurvivorNames.stream(seed, 7), (long) PEOPLE.size()));
        ApocalypseFirstLight.LOGGER.warn("[AFL Survivor] all {} roster people have been used in this world; {} ({}) returns",
                PEOPLE.size(), back.fullName(), back.id());
        return back;
    }

    private static List<Person> fresh(SurvivorRecord.Sex sex, Map<String, Long> lastSeen, Set<String> namesInUse) {
        List<Person> list = new ArrayList<>();
        for (Person person : PEOPLE) {
            if (person.sex() == sex && !lastSeen.containsKey(person.id())
                    && !namesInUse.contains(person.fullName().toLowerCase(Locale.ROOT))) {
                list.add(person);
            }
        }
        return list;
    }
}
