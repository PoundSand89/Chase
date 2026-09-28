import java.security.*;
import java.util.*;
import java.util.List;
import java.awt.*;
import java.awt.event.*;
import java.math.BigInteger;
import javax.swing.*;

/**
 * Chase.bet Keno - a provably-fair, single-player Keno variant inspired by
 * Stake's 40-number / 10-drawn format.  Standalone Java edition.
 *
 *   java ChaseKeno          -> Swing GUI (dark blue bg, yellow accents)
 *   java ChaseKeno test     -> self-test (provably-fair round-trip + RTP, no GUI)
 */
public class ChaseKeno {

    // ================================================================== #
    //  Provably-fair RNG
    // ================================================================== #
    public static final class ProvablyFair {
        private static final SecureRandom SECURE = new SecureRandom();
        private final String serverSeed;
        private final String clientSeed;
        private final int nonce;
        private final String seedHash;

        public ProvablyFair(String serverSeed, String clientSeed, int nonce) {
            if (serverSeed == null || serverSeed.isEmpty())
                throw new IllegalArgumentException("server seed required");
            this.serverSeed = serverSeed;
            this.clientSeed = (clientSeed == null) ? "" : clientSeed;
            this.nonce = nonce;
            this.seedHash = sha256(serverSeed + ":" + this.clientSeed + ":" + nonce);
        }

        public static String generateServerSeed() {
            byte[] b = new byte[16];
            SECURE.nextBytes(b);
            return String.format("%032x", new BigInteger(1, b));
        }

        public String getSeedHash() { return seedHash; }

        /** Deterministic draw; returns drawn tiles (sorted) + a proof object. */
        public DrawProof draw(int totalTiles, int drawCount, Set<Integer> picks) {
            String picksHash = sha256(sortedPicks(picks));
            String gameInput = seedHash + ":" + picksHash;
            long rngSeed = new BigInteger(gameInput.substring(0, 16), 16)
                    .mod(BigInteger.valueOf(1L << 32)).longValue();
            Random rng = new Random(rngSeed);
            List<Integer> tiles = new ArrayList<>(totalTiles);
            for (int i = 1; i <= totalTiles; i++) tiles.add(i);
            Collections.shuffle(tiles, rng);
            int[] drawn = new int[drawCount];
            for (int i = 0; i < drawCount; i++) drawn[i] = tiles.get(i);
            Arrays.sort(drawn);
            return new DrawProof(gameInput, drawn, picks, totalTiles, drawCount,
                    serverSeed, clientSeed, nonce, seedHash);
        }

        static String sha256(String s) {
            try {
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                byte[] h = md.digest(s.getBytes());
                return String.format("%064x", new BigInteger(1, h));
            } catch (NoSuchAlgorithmException e) {
                throw new RuntimeException(e);
            }
        }

        private static String sortedPicks(Set<Integer> picks) {
            int[] a = picks.stream().mapToInt(Integer::intValue).toArray();
            Arrays.sort(a);
            return Arrays.toString(a);
        }
    }

    /** Immutable bundle that lets anyone re-derive & verify a draw. */
    public static final class DrawProof {
        public final String gameInput;
        public final int[] drawnTiles;
        public final int[] picks;
        public final int totalTiles, drawCount, nonce;
        public final String serverSeed, clientSeed, seedHash, verificationHash;

        DrawProof(String gameInput, int[] drawn, Set<Integer> picks,
                  int totalTiles, int drawCount,
                  String serverSeed, String clientSeed, int nonce, String seedHash) {
            this.gameInput = gameInput;
            this.drawnTiles = drawn;
            int[] p = picks.stream().mapToInt(Integer::intValue).toArray();
            Arrays.sort(p);
            this.picks = p;
            this.totalTiles = totalTiles;
            this.drawCount = drawCount;
            this.serverSeed = serverSeed;
            this.clientSeed = clientSeed;
            this.nonce = nonce;
            this.seedHash = seedHash;
            this.verificationHash = ProvablyFair.sha256(payload());
        }

        private String payload() {
            return gameInput + Arrays.toString(drawnTiles)
                    + Arrays.toString(picks) + totalTiles + drawCount;
        }

        public boolean verify(ProvablyFair pf) {
            if (!pf.getSeedHash().equals(seedHash)) return false;
            DrawProof re = pf.draw(totalTiles, drawCount, toSet(picks));
            if (!Arrays.equals(re.drawnTiles, drawnTiles)) return false;
            return re.verificationHash.equals(verificationHash);
        }

        private static Set<Integer> toSet(int[] arr) {
            Set<Integer> s = new HashSet<>();
            for (int v : arr) s.add(v);
            return s;
        }
    }

    // ================================================================== #
    //  Payout tables (OTP-validated, ~0.99 RTP each).  index [spot][catch]
    // ================================================================== #
    public static final double[][] MEDIUM, CLASSIC, HIGH;
    static {
        MEDIUM = full(new Object[][]{
            {1,  new double[]{0.4, 2.75}},
            {2,  new double[]{0, 1.8, 5.1}},
            {3,  new double[]{0, 0, 2.8, 50}},
            {4,  new double[]{0, 0, 1.7, 10, 100}},
            {5,  new double[]{0, 0, 1.4, 4, 14, 390}},
            {6,  new double[]{0, 0, 0, 3, 9, 180, 710}},
            {7,  new double[]{0, 0, 0, 2, 7, 30, 400, 800}},
            {8,  new double[]{0, 0, 0, 2, 4, 11, 67, 400, 900}},
            {9,  new double[]{0, 0, 0, 2, 2.5, 5, 15, 100, 500, 1000}},
            {10, new double[]{0, 0, 0, 1.6, 2, 4, 7, 26, 100, 500, 1000}},
        });
        CLASSIC = full(new Object[][]{
            {1,  new double[]{0, 3.96}},
            {2,  new double[]{0, 1.9, 4.5}},
            {3,  new double[]{0, 1.0, 3.1, 10.4}},
            {4,  new double[]{0, 0.8, 1.8, 5, 22.5}},
            {5,  new double[]{0, 0.65, 1.14, 3.54, 12.03, 13.46}},
            {6,  new double[]{0, 0, 1.0, 3.68, 7, 16.5, 40}},
            {7,  new double[]{0, 0, 0.82, 2.4, 4.49, 12.05, 25.33, 44.94}},
            {8,  new double[]{0, 0, 0, 2.2, 4, 13, 22, 55, 70}},
            {9,  new double[]{0, 0, 0, 1.64, 2.85, 7.98, 13.67, 32.82, 54.7, 72.93}},
            {10, new double[]{0, 0, 0, 1.4, 2.25, 4.5, 8, 17, 50, 80, 100}},
        });
        HIGH = full(new Object[][]{
            {1,  new double[]{0, 3.96}},
            {2,  new double[]{0, 0, 17.1}},
            {3,  new double[]{0, 0, 0, 81.5}},
            {4,  new double[]{0, 0, 0, 10, 259}},
            {5,  new double[]{0, 0, 0, 4.5, 48, 450}},
            {6,  new double[]{0, 0, 0, 0, 11, 350, 710}},
            {7,  new double[]{0, 0, 0, 0, 4.88, 112.84, 298.88, 365.98}},
            {8,  new double[]{0, 0, 0, 0, 5, 20, 270, 600, 900}},
            {9,  new double[]{0, 0, 0, 0, 3.17, 10.45, 105.59, 247.37, 522.35, 596.97}},
            {10, new double[]{0, 0, 0, 0, 3.5, 8, 13, 63, 500, 800, 1000}},
        });
    }

    private static double[][] full(Object[][] rows) {
        double[][] t = new double[11][];
        for (int i = 0; i < 11; i++) t[i] = new double[11];
        for (Object[] r : rows) {
            int spot = (int) r[0];
            double[] vals = (double[]) r[1];
            System.arraycopy(vals, 0, t[spot], 0, vals.length);
        }
        return t;
    }

    public static double[][] tableFor(String risk) {
        return switch (risk) {
            case "High" -> HIGH;
            case "Classic" -> CLASSIC;
            default -> MEDIUM;
        };
    }

    // ================================================================== #
    //  Game round
    // ================================================================== #
    public static final int TOTAL_TILES = 40, DRAW_COUNT = 10;
    public static final double PLATFORM_FEE_PERCENT = 5.0, MAX_MULTIPLIER = 1000.0;

    public static final class Result {
        public final String player, riskLevel;
        public final double betSize, multiplier, platformFeeAmount, finalPayout, netWin;
        public final int[] picks, drawnTiles;
        public final int matches, pickCount;
        public final boolean win;
        public final DrawProof proof;
        public final double maxMultiplier;

        Result(String player, double betSize, int[] picks, int[] drawnTiles,
               int matches, double multiplier, double fee, double payout,
               double net, boolean win, String risk, DrawProof proof, double maxMult) {
            this.player = player; this.betSize = betSize; this.picks = picks;
            this.drawnTiles = drawnTiles; this.matches = matches; this.pickCount = picks.length;
            this.multiplier = multiplier; this.platformFeeAmount = fee;
            this.finalPayout = payout; this.netWin = net; this.win = win;
            this.riskLevel = risk; this.proof = proof; this.maxMultiplier = maxMult;
        }
    }

    public static Result resolveRound(double betSize, Set<Integer> picks, String risk,
                                     ProvablyFair pf, String player) {
        if (picks.size() < 1 || picks.size() > 10)
            throw new IllegalArgumentException("pick 1-10 tiles");
        DrawProof proof = pf.draw(TOTAL_TILES, DRAW_COUNT, picks);
        Set<Integer> drawn = new HashSet<>();
        for (int d : proof.drawnTiles) drawn.add(d);
        int matches = 0;
        for (int p : picks) if (drawn.contains(p)) matches++;
        double mult = tableFor(risk)[picks.size()][matches];
        double fee = Math.round(betSize * PLATFORM_FEE_PERCENT / 100.0 * 100.0) / 100.0;
        double gross = Math.round(betSize * mult * 100.0) / 100.0;
        double maxPayout = Math.round(betSize * MAX_MULTIPLIER * 100.0) / 100.0;
        double payout = Math.min(gross, maxPayout);
        double net = Math.round((payout - betSize - fee) * 100.0) / 100.0;
        return new Result(player, Math.round(betSize * 100.0) / 100.0,
                proof.picks, proof.drawnTiles, matches, mult, fee, payout,
                net, payout > 0, risk, proof, MAX_MULTIPLIER);
    }

    // ================================================================== #
    //  Self-test (headless)
    // ================================================================== #
    private static void selfTest() {
        System.out.println("=== Chase.bet Keno Java self-test (40 tiles, 10 drawn) ===");
        ProvablyFair pf = new ProvablyFair(ProvablyFair.generateServerSeed(),
                "demo-client", 1);
        Result r = resolveRound(10.0, Set.of(3, 9, 17, 23, 31, 40), "Medium", pf, "Alice");
        System.out.printf("Risk=%s Picks=%d Matches=%d Mult=%.2fx  Bet=$%.2f Fee=$%.2f Payout=$%.2f Net=$%.2f%n",
                r.riskLevel, r.pickCount, r.matches, r.multiplier, r.betSize,
                r.platformFeeAmount, r.finalPayout, r.netWin);
        System.out.println("SeedHash:      " + pf.getSeedHash());
        System.out.println("Verify (reveal): " + r.proof.verify(pf));

        // tamper: swap one drawn tile for one that was NOT drawn
        int[] bad = r.proof.drawnTiles.clone();
        int swapIn = 1;
        for (int i = 1; i <= 40; i++) { boolean in = false; for (int d : bad) if (d == i) in = true; if (!in) { swapIn = i; break; } }
        bad[0] = swapIn;
        Arrays.sort(bad);
        DrawProof tampered = new DrawProof(r.proof.gameInput, bad,
                DrawProof.toSet(r.proof.picks), r.proof.totalTiles, r.proof.drawCount,
                r.proof.serverSeed, r.proof.clientSeed, r.proof.nonce, r.proof.seedHash);
        ProvablyFair pf2 = new ProvablyFair(r.proof.serverSeed, r.proof.clientSeed, r.proof.nonce);
        System.out.println("Verify (tampered):  " + tampered.verify(pf2) + " (expected false)");

        System.out.println("\n=== RTP (pre-fee) ===");
        for (String risk : new String[]{"Classic", "Medium", "High"}) {
            double min = 9, max = 0, sum = 0;
            for (int n = 1; n <= 10; n++) {
                double rt = rtp(n, tableFor(risk));
                min = Math.min(min, rt); max = Math.max(max, rt); sum += rt;
            }
            System.out.printf("  %-8s min=%.3f max=%.3f avg=%.3f%n", risk, min, max, sum / 10);
        }
        System.out.println("\nSelf-test complete.");
    }

    private static double rtp(int n, double[][] table) {
        double sum = 0;
        for (int k = 0; k <= n; k++) sum += prob(n, k) * table[n][k];
        return sum;
    }
    private static double prob(int n, int k) {
        return (double) comb(n, k) * comb(40 - n, 10 - k) / comb(40, 10);
    }
    private static long comb(int n, int k) {
        if (k < 0 || k > n) return 0;
        if (k > n - k) k = n - k;
        long r = 1;
        for (int i = 1; i <= k; i++) r = r * (n - k + i) / i;
        return r;
    }

    // ================================================================== #
    //  Swing GUI  (dark blue background, yellow accents, Stake style)
    // ================================================================== #
    static final int TILE_SIZE = 54;

    /** Rounded border used for spot buttons / toolbar buttons. */
    static class RoundedBorder implements javax.swing.border.Border {
        private final int r; private final Color c;
        RoundedBorder(int r, Color c) { this.r = r; this.c = c; }
        public void paintBorder(Component comp, Graphics g, int x, int y, int w, int h) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(c); g2.setStroke(new BasicStroke(1f));
            g2.drawRoundRect(x, y, w - 1, h - 1, r, r);
            g2.dispose();
        }
        public Insets getBorderInsets(Component c) { return new Insets(4, 8, 4, 8); }
        public boolean isBorderOpaque() { return false; }
    }

    /** Circular tile.  Selected = yellow, drawn (winning) = white with gold ring,
     *  normal = muted blue.  Drawn is always visually distinct from selected. */
    static class TileButton extends JButton {
        private boolean sel = false, drw = false;
        TileButton(int n) {
            super(String.valueOf(n));
            setPreferredSize(new Dimension(TILE_SIZE, TILE_SIZE));
            setMinimumSize(new Dimension(TILE_SIZE, TILE_SIZE));
            setMaximumSize(new Dimension(TILE_SIZE, TILE_SIZE));
            setFocusPainted(false);
            setHorizontalAlignment(SwingConstants.CENTER);
            setVerticalAlignment(SwingConstants.CENTER);
            setFont(new Font("SansSerif", Font.BOLD, 15));
            setContentAreaFilled(false);
            setBorderPainted(false);
            setOpaque(false);
        }
        void setSel(boolean s) { sel = s; repaint(); }
        void setDrawn(boolean d) { drw = d; repaint(); }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();
            Color fill, ring, fg;
            if (drw) {
                fill = new Color(0xf4f7fb); ring = new Color(0xffd700); fg = new Color(0x1a2a4a);
            } else if (sel) {
                fill = new Color(0xffd700); ring = new Color(0xfff37a); fg = new Color(0x1a2a4a);
            } else {
                fill = new Color(0x3b7a8b); ring = new Color(0x8fa8b8); fg = new Color(0xe6e4de);
            }
            int arc = 8;
            g2.setColor(fill);
            g2.fillRoundRect(2, 2, w - 4, h - 4, arc, arc);
            g2.setColor(ring);
            g2.setStroke(new BasicStroke(2f));
            g2.drawRoundRect(2, 2, w - 4, h - 4, arc, arc);
            g2.setColor(fg);
            FontMetrics fm = g2.getFontMetrics(getFont());
            String s = getText();
            int x = (w - fm.stringWidth(s)) / 2;
            int y = (h - fm.getAscent() + fm.getDescent()) / 2 + fm.getAscent();
            g2.drawString(s, x, y);
            g2.dispose();
        }
        @Override protected void paintBorder(Graphics g) { }
    }

    static class SpotButton extends JToggleButton {
        SpotButton(int n) {
            super(String.valueOf(n));
            setPreferredSize(new Dimension(50, 36));
            setFocusPainted(false); setOpaque(true);
            setBorder(new RoundedBorder(12, new Color(0x8fa8b8)));
            setForeground(Color.WHITE); setBackground(new Color(0x1a3a5a));
            setFont(new Font("SansSerif", Font.BOLD, 12));
        }
        @Override public void setSelected(boolean s) {
            setBackground(s ? new Color(0xffd700) : new Color(0x1a3a5a));
            setForeground(s ? new Color(0x1a2a4a) : Color.WHITE);
            setBorder(new RoundedBorder(12, s ? new Color(0xfff37a) : new Color(0x8fa8b8)));
            super.setSelected(s);
        }
    }

    static class KenoFrame extends JFrame {
        private double balance = 1000.0, profit = 0.0, betSize = 10.0;
        private int multiplier = 2, nonce = 0, pickTarget = 10;
        private String risk = "Medium", clientSeed = "", mode = "Manual";
        private String serverSeed = ProvablyFair.generateServerSeed();
        private DrawProof currentProof = null;
        private boolean running = false;
        private javax.swing.Timer autoplayTimer;
        private int autoplayRemaining;
        private final Set<Integer> picks = new HashSet<>();

        private final TileButton[] tile = new TileButton[41];
        private final JLabel balanceLbl, payoutLbl, seedHashLbl, nonceLbl, revealLbl, verifyHashLbl, statusLbl;
        private final JTextField amountFld, clientFld;
        private final JSpinner gamesSpin;
        private final JButton drawBtn;
        private final List<SpotButton> spotBtns = new ArrayList<>();
        private final int[] MULTS = {1, 2, 5, 10, 25, 50};

        KenoFrame() {
            super("Chase.bet Keno");
            setDefaultCloseOperation(EXIT_ON_CLOSE);
            setSize(1300, 840);
            setLayout(new BorderLayout(12, 12));
            getContentPane().setBackground(new Color(0x0a1429));
            setLocationRelativeTo(null);

            // ---- top toolbar ----
            JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 8));
            top.setOpaque(false);
            JToggleButton modeBtn = new JToggleButton("Manual");
            modeBtn.setFocusable(false); modeBtn.setOpaque(true);
            modeBtn.setBorder(new RoundedBorder(14, new Color(0x8fa8b8)));
            modeBtn.setPreferredSize(new Dimension(90, 34));
            modeBtn.addActionListener(e -> {
                mode = modeBtn.isSelected() ? "Auto" : "Manual";
                modeBtn.setText(mode);
                modeBtn.setBackground(mode.equals("Auto") ? new Color(0xffd700) : new Color(0x1a3a5a));
                modeBtn.setForeground(mode.equals("Auto") ? new Color(0x1a2a4a) : Color.WHITE);
                modeBtn.setBorder(new RoundedBorder(14, mode.equals("Auto") ? new Color(0xfff37a) : new Color(0x8fa8b8)));
            });
            top.add(modeBtn);
            JButton multBtn = new JButton("2x");
            multBtn.setFocusable(false); multBtn.setOpaque(true);
            multBtn.setBorder(new RoundedBorder(14, new Color(0x8fa8b8)));
            multBtn.setPreferredSize(new Dimension(70, 34));
            multBtn.addActionListener(e -> cycleMult());
            top.add(multBtn);
            top.add(new JLabel("Games:"));
            gamesSpin = new JSpinner(new SpinnerNumberModel(1, 1, 1000, 1));
            gamesSpin.setMaximumSize(new Dimension(70, 28));
            gamesSpin.setBorder(new RoundedBorder(10, new Color(0x8fa8b8)));
            top.add(gamesSpin);
            for (String lvl : new String[]{"Classic", "Medium", "High"}) {
                JToggleButton b = new JToggleButton(lvl, lvl.equals(risk));
                b.setFocusable(false); b.setOpaque(true);
                b.setBorder(new RoundedBorder(14, new Color(0x8fa8b8)));
                b.setPreferredSize(new Dimension(90, 34));
                b.addActionListener(e -> setRisk(lvl));
                top.add(b);
            }
            top.add(button("Random Pick", e -> randomPick()));
            top.add(button("Clear Table", e -> clearTable()));
            top.add(new JLabel("Amount:"));
            amountFld = new JTextField("10.00", 8);
            top.add(amountFld);
            top.add(button("MAX", e -> setMaxBet()));
            top.add(button("Reset", e -> resetAll()));
            balanceLbl = new JLabel();
            top.add(balanceLbl);
            add(top, BorderLayout.NORTH);

            // ---- center board ----
            JPanel board = new JPanel(new GridLayout(5, 8, 5, 5));
            board.setOpaque(true);
            board.setBackground(new Color(0x0d2040));
            board.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(0x8fa8b8), 2),
                    BorderFactory.createEmptyBorder(10, 16, 10, 16)));
            for (int i = 1; i <= 40; i++) {
                TileButton b = new TileButton(i);
                int n = i;
                tile[i] = b;
                b.addActionListener(e -> toggleTile(n));
                board.add(b);
            }
            add(board, BorderLayout.CENTER);

            // ---- south: payout row + spot bar + PF strip + DRAW ----
            JPanel south = new JPanel();
            south.setLayout(new BoxLayout(south, BoxLayout.Y_AXIS));
            south.setOpaque(false);

            payoutLbl = new JLabel("", SwingConstants.CENTER);
            payoutLbl.setForeground(new Color(0xffd700));
            south.add(payoutLbl);

            JPanel spots = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 4));
            spots.setOpaque(false);
            for (int i = 1; i <= 10; i++) {
                SpotButton sb = new SpotButton(i);
                int n = i;
                sb.addActionListener(e -> setPickTarget(n));
                spotBtns.add(sb);
                spots.add(sb);
            }
            south.add(spots);

            JPanel pf = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
            pf.setOpaque(false);
            pf.add(new JLabel("Seed hash:"));
            seedHashLbl = new JLabel();
            seedHashLbl.setForeground(new Color(0xa0b8c8));
            seedHashLbl.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
            pf.add(seedHashLbl);
            pf.add(new JLabel("Revealed:"));
            revealLbl = new JLabel();
            pf.add(revealLbl);
            pf.add(new JLabel("Client:"));
            clientFld = new JTextField(10);
            clientFld.addActionListener(e -> refreshCommitment());
            pf.add(clientFld);
            pf.add(new JLabel("Nonce:"));
            nonceLbl = new JLabel();
            pf.add(nonceLbl);
            pf.add(new JLabel("Verify hash:"));
            verifyHashLbl = new JLabel();
            verifyHashLbl.setForeground(new Color(0xa0b8c8));
            verifyHashLbl.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
            pf.add(verifyHashLbl);
            pf.add(button("Verify", e -> verifyRound()));
            south.add(pf);

            JPanel drawRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 6));
            drawRow.setOpaque(false);
            drawBtn = button("DRAW", e -> runDraw());
            drawRow.add(drawBtn);
            statusLbl = new JLabel("Place your bet and pick 1-10 tiles.");
            statusLbl.setForeground(new Color(0xa0b8c8));
            drawRow.add(statusLbl);
            south.add(drawRow);
            add(south, BorderLayout.SOUTH);

            spotBtns.get(pickTarget - 1).setSelected(true);
            refreshBalance();
            refreshCommitment();
            updatePayout();
        }

        private JButton button(String text, ActionListener al) {
            JButton b = new JButton(text);
            b.setFocusable(false); b.setOpaque(true);
            b.setBorder(new RoundedBorder(14, new Color(0x8fa8b8)));
            b.setBackground(new Color(0x1a3a5a)); b.setForeground(Color.WHITE);
            b.setPreferredSize(new Dimension(120, 36));
            b.setFont(new Font("SansSerif", Font.BOLD, 12));
            b.addActionListener(al);
            return b;
        }

        private void refreshBalance() {
            balanceLbl.setText(String.format("Balance: $%,.2f   Profit: $%,.2f", balance, profit));
            balanceLbl.setForeground(balance >= 0 ? new Color(0xffd700) : Color.RED);
        }
        private void cycleMult() {
            int idx = 0;
            for (int i = 0; i < MULTS.length; i++) if (MULTS[i] == multiplier) idx = i;
            multiplier = MULTS[(idx + 1) % MULTS.length];
            refreshCommitment();
        }
        private void setRisk(String r) { this.risk = r; updatePayout(); }
        private void setMaxBet() {
            betSize = balance / Math.max(1, multiplier);
            amountFld.setText(String.format("%.2f", betSize));
        }
        private void randomPick() {
            clearTable();
            List<Integer> all = new ArrayList<>();
            for (int i = 1; i <= 40; i++) all.add(i);
            Collections.shuffle(all);
            for (int i = 0; i < pickTarget; i++) togglePick(all.get(i));
        }
        private void togglePick(int n) {
            if (picks.contains(n)) { picks.remove(n); tile[n].setSel(false); }
            else if (picks.size() < pickTarget) { picks.add(n); tile[n].setSel(true); }
        }
        private void toggleTile(int n) { togglePick(n); }
        private void clearTable() {
            picks.clear();
            for (int i = 1; i <= 40; i++) tile[i].setSel(false);
        }
        private void resetAll() {
            stopAutoplay();
            clearTable();
            serverSeed = ProvablyFair.generateServerSeed();
            nonce = 0; currentProof = null;
            balance = 1000.0; profit = 0.0; betSize = 10.0;
            amountFld.setText("10.00");
            revealLbl.setText(""); verifyHashLbl.setText("");
            statusLbl.setText("Place your bet and pick 1-10 tiles.");
            refreshBalance(); refreshCommitment(); updatePayout();
        }
        private void setPickTarget(int n) {
            pickTarget = n;
            for (int i = 0; i < 10; i++) spotBtns.get(i).setSelected(i == n - 1);
            updatePayout();
        }
        private void refreshCommitment() {
            clientSeed = clientFld.getText();
            ProvablyFair pf = new ProvablyFair(serverSeed, clientSeed, nonce);
            seedHashLbl.setText(pf.getSeedHash());
            nonceLbl.setText(String.valueOf(nonce));
        }
        private double totalBet() {
            try { betSize = Double.parseDouble(amountFld.getText()); } catch (Exception ex) { betSize = 0; }
            return betSize * multiplier;
        }
        private void runDraw() {
            if (running) return;
            double bet = totalBet();
            if (bet <= 0) { statusLbl.setText("Enter a positive bet."); return; }
            if (bet > balance) { statusLbl.setText("Insufficient balance."); return; }
            if (picks.isEmpty()) { statusLbl.setText("Pick some tiles first."); return; }
            ProvablyFair pf = new ProvablyFair(serverSeed, clientSeed, nonce);
            Result r = resolveRound(bet, picks, risk, pf, "Player");
            applyResult(r, pf);
            int g = (Integer) gamesSpin.getValue();
            if (g > 1) autoplay(g);
        }
        private void applyResult(Result r, ProvablyFair pf) {
            double totalCost = r.betSize + r.platformFeeAmount;
            balance = Math.round((balance - totalCost + r.finalPayout) * 100.0) / 100.0;
            profit = Math.round((profit + r.finalPayout - totalCost) * 100.0) / 100.0;
            refreshBalance();
            for (int i = 1; i <= 40; i++) {
                int n = i;
                tile[n].setDrawn(java.util.Arrays.stream(r.drawnTiles).anyMatch(x -> x == n));
            }
            revealLbl.setText(pf.serverSeed);
            currentProof = r.proof;
            verifyHashLbl.setText(r.proof.verificationHash);
            nonce++;
            refreshCommitment();
            statusLbl.setText((r.win ? "WIN " : "LOSE ") + r.matches + "/" + r.pickCount
                    + "  net $" + String.format("%.2f", r.netWin));
            statusLbl.setForeground(r.win ? new Color(0xffd700) : new Color(0xd96ba8));
            updatePayout();
        }
        private void updatePayout() {
            double[] row = tableFor(risk)[pickTarget];
            StringBuilder sb = new StringBuilder("Catch:");
            for (int c = 0; c <= pickTarget; c++) {
                double v = row[c];
                sb.append("  ").append(v == 0 ? "\u2014" : String.format("%.2fx", v));
            }
            payoutLbl.setText(sb.toString());
        }
        private void autoplay(int totalGames) {
            stopAutoplay();
            autoplayRemaining = totalGames - 1;
            running = true;
            autoplayTimer = new javax.swing.Timer(90, e -> {
                if (!running || autoplayRemaining <= 0) {
                    stopAutoplay();
                    return;
                }
                double bet = totalBet();
                if (bet <= 0 || bet > balance || picks.isEmpty()) {
                    stopAutoplay();
                    return;
                }
                ProvablyFair pf = new ProvablyFair(serverSeed, clientSeed, nonce);
                Result r = resolveRound(bet, picks, risk, pf, "Player");
                applyResult(r, pf);
                autoplayRemaining--;
                if (autoplayRemaining == 0) stopAutoplay();
            });
            autoplayTimer.setRepeats(true);
            autoplayTimer.start();
        }
        private void stopAutoplay() {
            running = false;
            if (autoplayTimer != null) {
                autoplayTimer.stop();
                autoplayTimer = null;
            }
            autoplayRemaining = 0;
        }
        private void verifyRound() {
            if (currentProof == null) {
                JOptionPane.showMessageDialog(this, "No round has been played yet.");
                return;
            }
            ProvablyFair pf = new ProvablyFair(currentProof.serverSeed,
                    currentProof.clientSeed, currentProof.nonce);
            boolean ok = currentProof.verify(pf);
            JOptionPane.showMessageDialog(this,
                    ok ? "VERIFIED \u2713 - the draw is provably fair." : "VERIFICATION FAILED \u2717");
        }
    }

    public static void main(String[] args) {
        if (args.length > 0 && "test".equalsIgnoreCase(args[0])) {
            selfTest();
            return;
        }
        SwingUtilities.invokeLater(() -> {
            try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); } catch (Exception ignored) {}
            new KenoFrame().setVisible(true);
        });
    }
}
