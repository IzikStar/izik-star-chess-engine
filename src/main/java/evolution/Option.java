package evolution;

import java.util.List;

/**
 * One setting an {@link Evolution} offers (its population size, how far it mutates...), so the Lab
 * can show it as a field with an explanation and the run can store the value chosen.
 *
 * @param key          the name the value is stored under
 * @param label        a short name for the field
 * @param help         what it does, in a sentence or two
 * @param defaultValue the value when nothing is chosen
 * @param min          the smallest number allowed (numbers only)
 * @param max          the largest number allowed (numbers only)
 * @param choices      the allowed values when the setting is a choice; empty for a number. A
 *                     {@link #multiple} choice holds several, comma separated.
 * @param multiple     several choices at once
 */
public record Option(String key, String label, String help, String defaultValue, double min, double max,
                     List<String> choices, boolean multiple) {

    public Option {
        choices = List.copyOf(choices);
    }

    /** A whole number between {@code min} and {@code max}. */
    public static Option number(String key, String label, String help, double defaultValue, double min, double max) {
        String text = defaultValue == Math.rint(defaultValue) ? String.valueOf((long) defaultValue) : String.valueOf(defaultValue);
        return new Option(key, label, help, text, min, max, List.of(), false);
    }

    /** One of {@code choices}. */
    public static Option choice(String key, String label, String help, String defaultValue, List<String> choices) {
        return new Option(key, label, help, defaultValue, 0, 0, choices, false);
    }

    /** Any of {@code choices}, comma separated. */
    public static Option several(String key, String label, String help, String defaultValue, List<String> choices) {
        return new Option(key, label, help, defaultValue, 0, 0, choices, true);
    }

    public boolean isNumber() {
        return choices.isEmpty();
    }

    /**
     * {@code value} checked against this option; throws {@link IllegalArgumentException} with a
     * message for the person who typed it.
     */
    public String check(String value) {
        String v = value.trim();
        if (isNumber()) {
            double d;
            try {
                d = Double.parseDouble(v);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(label + " is a number, not \"" + value + "\"");
            }
            if (d < min || d > max) {
                throw new IllegalArgumentException(label + " is " + fmt(min) + " to " + fmt(max) + ", not " + v);
            }
            return v;
        }
        for (String part : multiple ? v.split(",") : new String[] {v}) {
            if (!choices.contains(part.trim())) {
                throw new IllegalArgumentException(label + " is " + (multiple ? "any of " : "one of ") + choices + ", not " + part);
            }
        }
        return v;
    }

    private static String fmt(double d) {
        return d == Math.rint(d) ? String.valueOf((long) d) : String.valueOf(d);
    }
}
