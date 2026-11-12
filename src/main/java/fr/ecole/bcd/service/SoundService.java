package fr.ecole.bcd.service;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;
import java.awt.Toolkit;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Service audio haute performance pour le retour sonore en temps réel lors des scans à la douchette.
 * <p>
 * Ce composant offre des retours sonores clairs, perceptibles et non agressifs pour chaque interaction :
 * </p>
 * <ul>
 *   <li><b>Validation (Succès)</b> : bip aigu et net inspiré des douchettes laser professionnelles (Honeywell/Zebra).</li>
 *   <li><b>Problème (Erreur)</b> : double bourdonnement grave et discordant signalant immédiatement un arrêt ou un rejet.</li>
 *   <li><b>Avertissement (Warning)</b> : tonalité tempérée attirant l'attention sur les doublons ou confirmations nécessaires.</li>
 * </ul>
 * <p>
 * Particularités techniques :
 * </p>
 * <ul>
 *   <li>Synthèse PCM 16-bit 44.1 kHz autonome en mémoire via l'API Java Sound standard (aucune ressource MP3/WAV externe requise).</li>
 *   <li>Mise en cache statique des formes d'ondes pour une latence de restitution nulle (&lt; 1 ms).</li>
 *   <li>Exécution asynchrone non-bloquante déléguée à un {@link ExecutorService} mono-thread dédié haute priorité.</li>
 *   <li>Mécanisme de repli sécurisé (fallback) avec le bip système si la carte son est indisponible ou non prise en charge.</li>
 * </ul>
 *
 * @author JC ART
 * @since 2026-10-12
 * @version 1.0 (Dernière modification : 2026-11-10)
 */
public final class SoundService {

    /**
     * Fréquence d'échantillonnage audio standard (44 100 Hz, qualité CD).
     */
    private static final int SAMPLE_RATE = 44100;

    /**
     * Indicateur d'activation globale des signaux sonores.
     */
    private static volatile boolean soundEnabled = true;

    /**
     * Facteur de volume d'amplification (compris entre 0.0 et 1.0).
     */
    private static volatile double volume = 0.85;

    /**
     * Tampon d'échantillons PCM pré-généré pour le bip de succès.
     */
    private static final byte[] PCM_SUCCESS = generateSuccessPcm();

    /**
     * Tampon d'échantillons PCM pré-généré pour le signal d'erreur.
     */
    private static final byte[] PCM_ERROR = generateErrorPcm();

    /**
     * Tampon d'échantillons PCM pré-généré pour le signal d'avertissement.
     */
    private static final byte[] PCM_WARNING = generateWarningPcm();

    /**
     * Exécuteur asynchrone pour la lecture audio sur thread d'arrière-plan sans saccade de l'IHM JavaFX.
     */
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "BcdSoundService-Worker");
        t.setDaemon(true);
        t.setPriority(Thread.MAX_PRIORITY - 1);
        return t;
    });

    /**
     * Constructeur privé empêchant l'instanciation de cette classe utilitaire.
     */
    private SoundService() {
        // Classe utilitaire
    }

    /**
     * Ferme proprement le pool de threads audio lors de la fermeture de l'application.
     */
    public static void shutdown() {
        EXECUTOR.shutdownNow();
    }

    /**
     * Active ou désactive l'ensemble des retours sonores de l'application.
     *
     * @param enabled {@code true} pour activer le son, {@code false} pour couper tous les bips
     */
    public static void setSoundEnabled(boolean enabled) {
        soundEnabled = enabled;
    }

    /**
     * Indique si les sons sont actuellement activés.
     *
     * @return {@code true} si le son est activé, {@code false} sinon
     */
    public static boolean isSoundEnabled() {
        return soundEnabled;
    }

    /**
     * Règle le niveau de volume sonore.
     *
     * @param vol le volume souhaité entre 0.0 (muet) et 1.0 (maximum)
     */
    public static void setVolume(double vol) {
        volume = Math.max(0.0, Math.min(1.0, vol));
    }

    /**
     * Retourne le niveau de volume sonore actuel.
     *
     * @return le volume entre 0.0 et 1.0
     */
    public static double getVolume() {
        return volume;
    }

    /**
     * Émet le bip de validation (scan réussi d'un livre / enregistrement effectué).
     * <p>
     * Fréquence montante aiguë très nette (1450 Hz &rarr; 1950 Hz, durée ~90 ms) avec micro fade-in/fade-out anti-clic.
     * </p>
     */
    public static void playSuccess() {
        if (!soundEnabled) return;
        EXECUTOR.submit(() -> playRawPcm(PCM_SUCCESS));
    }

    /**
     * Alias sémantique de {@link #playSuccess()}.
     */
    public static void playSuccessSound() {
        playSuccess();
    }

    /**
     * Émet le signal sonore de problème ou d'erreur (livre inconnu, erreur de scan, code-barres invalide).
     * <p>
     * Double bourdonnement grave et percutant (220 Hz puis 175 Hz discordant, durée ~300 ms).
     * </p>
     */
    public static void playError() {
        if (!soundEnabled) return;
        EXECUTOR.submit(() -> playRawPcm(PCM_ERROR));
    }

    /**
     * Alias sémantique de {@link #playError()}.
     */
    public static void playProblemSound() {
        playError();
    }

    /**
     * Émet le signal d'avertissement (livre doublon nécessitant un choix, exemplaire réservé, etc.).
     * <p>
     * Double impulsion médium douce (780 Hz, durée ~180 ms).
     * </p>
     */
    public static void playWarning() {
        if (!soundEnabled) return;
        EXECUTOR.submit(() -> playRawPcm(PCM_WARNING));
    }

    /**
     * Joue un flux binaire PCM brut 16-bit sur une ligne {@link SourceDataLine} avec ajustement dynamique du volume.
     *
     * @param pcm les octets PCM bruts
     */
    private static void playRawPcm(byte[] pcm) {
        if (pcm == null || pcm.length == 0) return;
        try {
            AudioFormat format = new AudioFormat(SAMPLE_RATE, 16, 1, true, false);
            DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);

            if (!AudioSystem.isLineSupported(info)) {
                fallbackBeep();
                return;
            }

            // Application du volume dynamique sur une copie ajustée si nécessaire
            byte[] playbackData = pcm;
            double currentVol = volume;
            if (Math.abs(currentVol - 1.0) > 0.01) {
                playbackData = new byte[pcm.length];
                for (int i = 0; i < pcm.length - 1; i += 2) {
                    short sample = (short) ((pcm[i] & 0xFF) | (pcm[i + 1] << 8));
                    sample = (short) Math.round(sample * currentVol);
                    playbackData[i] = (byte) (sample & 0xFF);
                    playbackData[i + 1] = (byte) ((sample >> 8) & 0xFF);
                }
            }

            try (SourceDataLine line = (SourceDataLine) AudioSystem.getLine(info)) {
                line.open(format, playbackData.length);
                line.start();
                line.write(playbackData, 0, playbackData.length);
                line.drain();
            }
        } catch (Throwable t) {
            fallbackBeep();
        }
    }

    /**
     * Déclenche le bip système standard en cas d'indisponibilité du sous-système audio Java Sound.
     */
    private static void fallbackBeep() {
        try {
            Toolkit.getDefaultToolkit().beep();
        } catch (Throwable ignored) {
            // Environnement totalement sans tête ou sans carte audio
        }
    }

    // =========================================================================
    //  Générateurs de formes d'ondes PCM 16-bit
    // =========================================================================

    /**
     * Synthétise en mémoire la forme d'onde PCM du bip de validation aigu montant (1450 Hz &rarr; 1950 Hz).
     *
     * @return le tableau d'octets PCM prêt pour la lecture
     */
    private static byte[] generateSuccessPcm() {
        int msTone1 = 35;
        int msTone2 = 55;
        int totalSamples = (SAMPLE_RATE * (msTone1 + msTone2)) / 1000;
        byte[] data = new byte[totalSamples * 2];
        int idx = 0;

        int samples1 = (SAMPLE_RATE * msTone1) / 1000;
        int samples2 = totalSamples - samples1;

        double freq1 = 1450.0;
        double freq2 = 1950.0;
        double amplitude = 23000.0;

        // Première fréquence (1450 Hz)
        for (int i = 0; i < samples1; i++) {
            double angle = 2.0 * Math.PI * i * freq1 / SAMPLE_RATE;
            double env = 1.0;
            if (i < 180) env = (double) i / 180.0; // Fade-in 4 ms
            short sample = (short) (Math.sin(angle) * amplitude * env);
            data[idx++] = (byte) (sample & 0xFF);
            data[idx++] = (byte) ((sample >> 8) & 0xFF);
        }

        // Deuxième fréquence montante (1950 Hz)
        for (int i = 0; i < samples2; i++) {
            double angle = 2.0 * Math.PI * i * freq2 / SAMPLE_RATE;
            double env = 1.0;
            if (i >= samples2 - 350) env = (double) (samples2 - i) / 350.0; // Fade-out 8 ms
            short sample = (short) (Math.sin(angle) * amplitude * env);
            data[idx++] = (byte) (sample & 0xFF);
            data[idx++] = (byte) ((sample >> 8) & 0xFF);
        }

        return data;
    }

    /**
     * Synthétise en mémoire la forme d'onde PCM du signal d'erreur grave et discordant à deux impulsions (BZZT - BZZT).
     *
     * @return le tableau d'octets PCM prêt pour la lecture
     */
    private static byte[] generateErrorPcm() {
        int msPulse1 = 110;
        int msSilence = 35;
        int msPulse2 = 140;
        int totalSamples = (SAMPLE_RATE * (msPulse1 + msSilence + msPulse2)) / 1000;
        byte[] data = new byte[totalSamples * 2];
        int idx = 0;

        int s1 = (SAMPLE_RATE * msPulse1) / 1000;
        int sSilence = (SAMPLE_RATE * msSilence) / 1000;
        int s2 = totalSamples - s1 - sSilence;
        double amplitude = 24500.0;

        // Impulsion 1 : 220 Hz + tierce mineure 277 Hz (frottement d'avertissement)
        for (int i = 0; i < s1; i++) {
            double t = (double) i / SAMPLE_RATE;
            double wave = 0.65 * Math.sin(2.0 * Math.PI * 220.0 * t) + 0.35 * Math.sin(2.0 * Math.PI * 277.0 * t);
            double env = 1.0;
            if (i < 180) env = (double) i / 180.0;
            if (i >= s1 - 250) env = (double) (s1 - i) / 250.0;
            short sample = (short) (wave * amplitude * env);
            data[idx++] = (byte) (sample & 0xFF);
            data[idx++] = (byte) ((sample >> 8) & 0xFF);
        }

        // Silence intermédiaire
        for (int i = 0; i < sSilence; i++) {
            data[idx++] = 0;
            data[idx++] = 0;
        }

        // Impulsion 2 : 175 Hz + 220 Hz
        for (int i = 0; i < s2; i++) {
            double t = (double) i / SAMPLE_RATE;
            double wave = 0.65 * Math.sin(2.0 * Math.PI * 175.0 * t) + 0.35 * Math.sin(2.0 * Math.PI * 220.0 * t);
            double env = 1.0;
            if (i < 180) env = (double) i / 180.0;
            if (i >= s2 - 350) env = (double) (s2 - i) / 350.0;
            short sample = (short) (wave * amplitude * env);
            data[idx++] = (byte) (sample & 0xFF);
            data[idx++] = (byte) ((sample >> 8) & 0xFF);
        }

        return data;
    }

    /**
     * Synthétise en mémoire la forme d'onde PCM du bip d'avertissement médium (780 Hz) doux à deux impulsions.
     *
     * @return le tableau d'octets PCM prêt pour la lecture
     */
    private static byte[] generateWarningPcm() {
        int msTone1 = 70;
        int msSilence = 25;
        int msTone2 = 70;
        int totalSamples = (SAMPLE_RATE * (msTone1 + msSilence + msTone2)) / 1000;
        byte[] data = new byte[totalSamples * 2];
        int idx = 0;

        int s1 = (SAMPLE_RATE * msTone1) / 1000;
        int sSilence = (SAMPLE_RATE * msSilence) / 1000;
        int s2 = totalSamples - s1 - sSilence;
        double freq = 780.0;
        double amplitude = 20000.0;

        // Impulsion 1
        for (int i = 0; i < s1; i++) {
            double angle = 2.0 * Math.PI * i * freq / SAMPLE_RATE;
            double env = 1.0;
            if (i < 150) env = (double) i / 150.0;
            if (i >= s1 - 200) env = (double) (s1 - i) / 200.0;
            short sample = (short) (Math.sin(angle) * amplitude * env);
            data[idx++] = (byte) (sample & 0xFF);
            data[idx++] = (byte) ((sample >> 8) & 0xFF);
        }

        // Silence
        for (int i = 0; i < sSilence; i++) {
            data[idx++] = 0;
            data[idx++] = 0;
        }

        // Impulsion 2
        for (int i = 0; i < s2; i++) {
            double angle = 2.0 * Math.PI * i * freq / SAMPLE_RATE;
            double env = 1.0;
            if (i < 150) env = (double) i / 150.0;
            if (i >= s2 - 200) env = (double) (s2 - i) / 200.0;
            short sample = (short) (Math.sin(angle) * amplitude * env);
            data[idx++] = (byte) (sample & 0xFF);
            data[idx++] = (byte) ((sample >> 8) & 0xFF);
        }

        return data;
    }
}
