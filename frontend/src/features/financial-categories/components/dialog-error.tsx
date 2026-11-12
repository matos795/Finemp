export function DialogError({ message }: { message: string }) {
    return message ? (
        <div className="err" role="alert" style={{ marginTop: 10 }}>{message}</div>
    ) : null
}
