export interface ReceivedPaymentMethodTotal {
    paymentMethod: string
    paymentCount: number
    totalAmount: string
}

export interface ReceivedPaymentsByMethodReport {
    from: string
    to: string
    totalCount: number
    totalAmount: string
    methods: ReceivedPaymentMethodTotal[]
}
