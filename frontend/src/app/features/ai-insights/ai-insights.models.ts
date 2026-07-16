/**
 * Domain models for AiInsightsModule.
 *
 * These mirror the DTOs that ai-service's endpoints produce/consume:
 *
 *   GET /api/ai/summary/{symbol}       → AiSummary
 *   GET /api/ai/signals/{symbol}       → AiSignal[]
 *   POST /api/ai/journal-critique      → JournalCritique
 *
 * LLM responses may be slow; all requests should show a loading state.
 */

/** AI-generated market summary for a symbol. */
export interface AiSummary {
  symbol:     string;
  summary:    string;            // Markdown or plain-text LLM output
  sentiment:  'BULLISH' | 'BEARISH' | 'NEUTRAL';
  confidence: number;            // 0–1 float
  generatedAt: string;           // ISO-8601
}

/** A single AI-generated trade signal. */
export interface AiSignal {
  id:         string;
  symbol:     string;
  type:       'BUY' | 'SELL' | 'HOLD';
  reasoning:  string;
  confidence: number;            // 0–1 float
  targetPrice: number | null;
  stopLoss:   number | null;
  generatedAt: string;           // ISO-8601
}

/** Request body for journal-critique endpoint. */
export interface JournalCritiqueRequest {
  tradeId:     string;
  symbol:      string;
  side:        'BUY' | 'SELL';
  entryPrice:  number;
  exitPrice:   number | null;
  notes:       string | null;
}

/** AI critique of a specific trade from the journal. */
export interface JournalCritique {
  tradeId:    string;
  critique:   string;            // Markdown/plain LLM output
  rating:     'GOOD' | 'AVERAGE' | 'POOR';
  suggestions: string[];
  generatedAt: string;
}
